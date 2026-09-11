package com.zanconsultancy.stripepayment.service;

import tools.jackson.databind.ObjectMapper;
import com.zanconsultancy.stripepayment.config.AppProperties;
import com.zanconsultancy.stripepayment.model.OrderDraft;
import com.zanconsultancy.stripepayment.model.OrderRecord;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class OrderStore {
  private static final int MAX_EVENTS = 100;
  private final ObjectMapper mapper;
  private final Path path;
  private final ReentrantLock lock = new ReentrantLock(true);
  private StoreData data = new StoreData();

  public OrderStore(ObjectMapper mapper, AppProperties props) {
    this.mapper = mapper;
    this.path = props.orderStorePath().toAbsolutePath().normalize();
  }

  @PostConstruct
  public void initialize() throws IOException {
    Path parent = path.getParent();
    if (parent != null) Files.createDirectories(parent);
    if (Files.exists(path)) data = mapper.readValue(path.toFile(), StoreData.class);
    else persist();
  }

  public OrderRecord createOrGet(String token, String hash, OrderDraft draft, String customerId, Supplier<String> id) {
    return exclusive(() -> {
      Optional<OrderRecord> existing = data.orders.stream().filter(o -> token.equals(o.checkoutToken)).findFirst();
      if (existing.isPresent()) {
        if (!hash.equals(existing.get().cartHash)) throw new CartChangedException();
        return existing.get();
      }
      OrderRecord order = OrderRecord.create(id.get(), token, hash, draft, customerId);
      data.orders.add(order);
      persistUnchecked();
      return order;
    });
  }

  public OrderRecord attachPaymentIntent(String orderId, String paymentIntentId, String paymentStatus) {
    return exclusive(() -> {
      OrderRecord order = requireById(orderId);
      if (order.paymentIntentId != null && !order.paymentIntentId.equals(paymentIntentId)) {
        throw new IllegalStateException("Order already has a different PaymentIntent");
      }
      order.paymentIntentId = paymentIntentId;
      if (!"succeeded".equals(order.paymentStatus) && order.lastStripeEventCreated == 0) order.paymentStatus = paymentStatus == null ? "requires_payment_method" : paymentStatus;
      touch(order);
      persistUnchecked();
      return order;
    });
  }

  public Optional<OrderRecord> findByPaymentIntent(String paymentIntentId) {
    return exclusive(() -> data.orders.stream().filter(o -> paymentIntentId.equals(o.paymentIntentId)).findFirst());
  }

  public EventResult applyStripeEvent(String eventId, long created, String orderId, String paymentIntentId, String status) {
    return exclusive(() -> {
      OrderRecord order = data.orders.stream().filter(o -> paymentIntentId.equals(o.paymentIntentId)).findFirst().orElse(null);
      if (order == null && orderId != null) {
        order = data.orders.stream().filter(o -> orderId.equals(o.id)).findFirst().orElse(null);
        if (order != null && order.paymentIntentId == null) order.paymentIntentId = paymentIntentId;
        else if (order != null && !paymentIntentId.equals(order.paymentIntentId)) order = null;
      }
      if (order == null) return new EventResult(false, false, false, null);
      if (order.processedEventIds.contains(eventId)) return new EventResult(true, true, false, order.id);

      boolean stale = created < order.lastStripeEventCreated;
      boolean wouldDowngradeSuccess = "succeeded".equals(order.paymentStatus) && !"succeeded".equals(status);

      order.processedEventIds.add(eventId);
      if (order.processedEventIds.size() > MAX_EVENTS) {
        order.processedEventIds = new ArrayList<>(order.processedEventIds.subList(order.processedEventIds.size() - MAX_EVENTS, order.processedEventIds.size()));
      }

      if (!stale && !wouldDowngradeSuccess) {
        order.lastStripeEventCreated = created;
        order.paymentStatus = status;
        if ("succeeded".equals(status)) {
          order.fulfillmentStatus = "ready_for_fulfillment";
          order.paidAt = Instant.ofEpochSecond(created);
        } else if ("canceled".equals(status) || "requires_payment_method".equals(status)) {
          order.fulfillmentStatus = "unfulfilled";
        }
      }

      touch(order);
      persistUnchecked();
      return new EventResult(true, false, stale || wouldDowngradeSuccess, order.id);
    });
  }

  private OrderRecord requireById(String id) {
    return data.orders.stream().filter(o -> id.equals(o.id)).findFirst().orElseThrow();
  }

  private void touch(OrderRecord order) {
    order.updatedAt = Instant.now();
  }

  private <T> T exclusive(Supplier<T> action) {
    lock.lock();
    byte[] before = null;
    try {
      before = mapper.writeValueAsBytes(data);
      return action.get();
    } catch (RuntimeException failure) {
      // A failed disk write must not leave an event acknowledged only in memory.
      if (before != null) data = mapper.readValue(before, StoreData.class);
      throw failure;
    } finally {
      lock.unlock();
    }
  }

  private void persistUnchecked() {
    try {
      persist();
    } catch (IOException | tools.jackson.core.JacksonException e) {
      throw new IllegalStateException("Unable to persist order store", e);
    }
  }

  private void persist() throws IOException {
    Path temp = path.resolveSibling(path.getFileName() + ".tmp");
    mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), data);
    try {
      Files.setPosixFilePermissions(temp, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    } catch (UnsupportedOperationException ignored) {}
    try {
      Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException ignored) {
      Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  public List<OrderRecord> all() { return exclusive(() -> List.copyOf(data.orders)); }
  public OrderRecord require(String id) { return exclusive(() -> requireById(id)); }
  public void recordTransfer(String id,String role,String transfer) { exclusive(() -> { requireById(id).transfers.put(role,transfer); persistUnchecked(); return null; }); }
  public void reset() { exclusive(() -> { data = new StoreData(); persistUnchecked(); return null; }); }
  public record EventResult(boolean found, boolean duplicate, boolean stale, String orderId) {}
  public static class StoreData {
    public int version = 1;
    public List<OrderRecord> orders = new ArrayList<>();
  }
}

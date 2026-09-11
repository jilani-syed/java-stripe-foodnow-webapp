package com.zanconsultancy.stripepayment;

import static org.junit.jupiter.api.Assertions.*;
import tools.jackson.databind.ObjectMapper;
import com.zanconsultancy.stripepayment.config.AppProperties;
import com.zanconsultancy.stripepayment.model.*;
import com.zanconsultancy.stripepayment.service.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class OrderStoreTests {
  @TempDir Path temp;
  private OrderStore store;
  private final OrderDraft draft = new OrderDraft(List.of(new OrderLine("1", "Dish", 1, 2300, 2300)), 1, 2300, "usd");

  @BeforeEach
  void setup() throws Exception {
    store = new OrderStore(new ObjectMapper(), new AppProperties(temp.resolve("orders.json")));
    store.initialize();
  }

  @Test
  void reusesCheckoutAndRejectsChangedCart() {
    var first = store.createOrGet("token", "hash", draft, "cus_one", () -> "ord_one");
    assertSame(first, store.createOrGet("token", "hash", draft, "cus_one", () -> "ord_two"));
    assertEquals("cus_one", first.customerId);
    assertThrows(CartChangedException.class, () -> store.createOrGet("token", "different", draft, "cus_two", () -> "ord_two"));
  }

  @Test
  void persistsAssociationAndProtectsSucceededState() throws Exception {
    store.createOrGet("token", "hash", draft, "cus_one", () -> "ord_one");
    store.attachPaymentIntent("ord_one", "pi_one", "requires_payment_method");
    var success = store.applyStripeEvent("evt_1", 20, "ord_one", "pi_one", "succeeded");
    assertTrue(success.found());
    assertFalse(success.duplicate());
    assertTrue(store.applyStripeEvent("evt_1", 20, "ord_one", "pi_one", "succeeded").duplicate());
    assertTrue(store.applyStripeEvent("evt_old", 10, "ord_one", "pi_one", "requires_payment_method").stale());
    OrderStore reloaded = new OrderStore(new ObjectMapper(), new AppProperties(temp.resolve("orders.json")));
    reloaded.initialize();
    assertEquals("succeeded", reloaded.findByPaymentIntent("pi_one").orElseThrow().paymentStatus);
  }

  @Test
  void reconcilesEarlyWebhookFromOrderMetadata() {
    store.createOrGet("token", "hash", draft, "cus_one", () -> "ord_one");
    assertTrue(store.applyStripeEvent("evt_early", 20, "ord_one", "pi_early", "processing").found());
    assertEquals("pi_early", store.findByPaymentIntent("pi_early").orElseThrow().paymentIntentId);
  }

  @Test
  void refusesWebhookForDifferentIntentOnAssociatedOrder() {
    store.createOrGet("token", "hash", draft, "cus_one", () -> "ord_one");
    store.attachPaymentIntent("ord_one", "pi_expected", "requires_payment_method");
    assertFalse(store.applyStripeEvent("evt_wrong", 20, "ord_one", "pi_wrong", "succeeded").found());
    assertEquals("requires_payment_method", store.findByPaymentIntent("pi_expected").orElseThrow().paymentStatus);
  }
  @Test
  void failedPersistDoesNotAcknowledgeEventInMemory() throws Exception {
    store.createOrGet("t", "h", draft, "cus_one", () -> "ord_fail");
    store.attachPaymentIntent("ord_fail", "pi_fail", "requires_payment_method");
    java.nio.file.Files.createDirectory(temp.resolve("orders.json.tmp"));
    assertThrows(IllegalStateException.class, () -> store.applyStripeEvent("evt_fail", 30, "ord_fail", "pi_fail", "succeeded"));
    assertEquals("requires_payment_method", store.require("ord_fail").paymentStatus);
    assertEquals(0, store.require("ord_fail").processedEventIds.size());
    java.nio.file.Files.delete(temp.resolve("orders.json.tmp"));
    assertFalse(store.applyStripeEvent("evt_fail", 30, "ord_fail", "pi_fail", "succeeded").duplicate());
  }
}

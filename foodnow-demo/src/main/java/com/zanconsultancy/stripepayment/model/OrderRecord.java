package com.zanconsultancy.stripepayment.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class OrderRecord {
  public String id;
  public java.util.Map<String,String> transfers = new java.util.LinkedHashMap<>();
  public String checkoutToken;
  public String cartHash;
  public String customerId;
  public List<OrderLine> lineItems = new ArrayList<>();
  public int itemCount;
  public long amount;
  public String currency;
  public String paymentIntentId;
  public String paymentStatus = "not_started";
  public String fulfillmentStatus = "unfulfilled";
  public List<String> processedEventIds = new ArrayList<>();
  public long lastStripeEventCreated;
  public Instant createdAt;
  public Instant updatedAt;
  public Instant paidAt;

  public OrderRecord() {}

  public static OrderRecord create(String id, String token, String hash, OrderDraft draft, String customerId) {
    OrderRecord order = new OrderRecord();
    order.id = id;
    order.checkoutToken = token;
    order.cartHash = hash;
    order.customerId = customerId;
    order.lineItems = new ArrayList<>(draft.lineItems());
    order.itemCount = draft.itemCount();
    order.amount = draft.amount();
    order.currency = draft.currency();
    order.createdAt = order.updatedAt = Instant.now();
    return order;
  }
}

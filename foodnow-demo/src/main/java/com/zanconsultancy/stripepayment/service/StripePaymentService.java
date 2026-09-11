package com.zanconsultancy.stripepayment.service;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.zanconsultancy.stripepayment.config.StripeProperties;
import com.zanconsultancy.stripepayment.model.OrderRecord;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class StripePaymentService {
  private final StripeProperties stripe;

  public StripePaymentService(StripeProperties stripe) {
    this.stripe = stripe;
  }

  public PaymentIntent create(OrderRecord order) throws StripeException {
    String dishIds = order.lineItems.stream().map(line -> line.dishId()).collect(Collectors.joining(","));
    var params = PaymentIntentCreateParams.builder()
        .setAmount(order.amount)
        .setCurrency(order.currency)
        .setCustomer(order.customerId)
        .setTransferGroup(order.id)
        .setDescription("FoodNow order (" + order.itemCount + " dishes)")
        .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build())
        .putMetadata("application", "FoodNow")
        .putMetadata("order_id", order.id)
        .putMetadata("dish_ids", dishIds)
        .putMetadata("item_count", Integer.toString(order.itemCount))
        .build();
    return PaymentIntent.create(params, options("payment_intent_" + order.id));
  }

  public PaymentIntent retrieve(String id) throws StripeException {
    return PaymentIntent.retrieve(id, options(null));
  }

  private RequestOptions options(String idempotencyKey) {
    var builder = RequestOptions.builder().setApiKey(stripe.secretKey());
    if (idempotencyKey != null) builder.setIdempotencyKey(idempotencyKey);
    return builder.build();
  }
}

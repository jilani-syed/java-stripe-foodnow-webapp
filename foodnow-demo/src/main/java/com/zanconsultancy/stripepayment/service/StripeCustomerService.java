package com.zanconsultancy.stripepayment.service;

import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.ChargeCollection;
import com.stripe.model.Customer;
import com.stripe.model.CustomerCollection;
import com.stripe.net.RequestOptions;
import com.stripe.param.ChargeListParams;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerListParams;
import com.zanconsultancy.stripepayment.config.StripeProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class StripeCustomerService {
  private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  private final StripeProperties stripe;

  public StripeCustomerService(StripeProperties stripe) {
    this.stripe = stripe;
  }

  public String normalizeEmail(String value) {
    if (value == null) return null;
    String email = value.trim().toLowerCase(Locale.ROOT);
    if (email.isEmpty() || email.length() > 254 || !EMAIL_PATTERN.matcher(email).matches()) return null;
    return email;
  }

  public Customer findByEmail(String email) throws StripeException {
    CustomerListParams params = CustomerListParams.builder()
        .setEmail(email)
        .setLimit(1L)
        .build();
    CustomerCollection customers = Customer.list(params, options(null));
    return customers.getData().isEmpty() ? null : customers.getData().getFirst();
  }

  public Customer findOrCreate(String email) throws StripeException {
    Customer existing = findByEmail(email);
    if (existing != null) return existing;

    CustomerCreateParams params = CustomerCreateParams.builder()
        .setEmail(email)
        .putMetadata("source", "foodnow_checkout")
        .build();
    return Customer.create(params, options("customer_email_" + sha256(email)));
  }

  public ChargeHistory listCharges(String email, int limit) throws StripeException {
    Customer customer = findByEmail(email);
    if (customer == null) return new ChargeHistory(null, List.of(), false);

    ChargeListParams params = ChargeListParams.builder()
        .setCustomer(customer.getId())
        .setLimit((long) limit)
        .build();
    ChargeCollection charges = Charge.list(params, options(null));
    List<ChargeSummary> summaries = charges.getData().stream().map(this::summarize).toList();
    return new ChargeHistory(customer.getId(), summaries, Boolean.TRUE.equals(charges.getHasMore()));
  }

  public boolean testMode() {
    return stripe.secretKey() != null && stripe.secretKey().startsWith("sk_test_");
  }

  private ChargeSummary summarize(Charge charge) {
    return new ChargeSummary(
        charge.getId(),
        charge.getPaymentIntent(),
        charge.getAmount(),
        charge.getAmountCaptured(),
        charge.getAmountRefunded(),
        charge.getCurrency(),
        charge.getStatus(),
        Boolean.TRUE.equals(charge.getPaid()),
        Boolean.TRUE.equals(charge.getRefunded()),
        Boolean.TRUE.equals(charge.getDisputed()),
        charge.getCreated());
  }

  private RequestOptions options(String idempotencyKey) {
    RequestOptions.RequestOptionsBuilder builder = RequestOptions.builder().setApiKey(stripe.secretKey());
    if (idempotencyKey != null) builder.setIdempotencyKey(idempotencyKey);
    return builder.build();
  }

  private String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  public record ChargeHistory(String customerId, List<ChargeSummary> charges, boolean hasMore) {}

  public record ChargeSummary(
      String id,
      String paymentIntentId,
      Long amount,
      Long amountCaptured,
      Long amountRefunded,
      String currency,
      String status,
      boolean paid,
      boolean refunded,
      boolean disputed,
      Long created) {}
}

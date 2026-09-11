package com.zanconsultancy.stripepayment.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.zanconsultancy.stripepayment.model.OrderDraft;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class CheckoutService {
  private static final Pattern UUID_V4 = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);
  private final ObjectMapper mapper;

  public CheckoutService(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public void validateToken(String token) {
    if (token == null || !UUID_V4.matcher(token).matches()) {
      throw new IllegalArgumentException("A valid checkout token is required");
    }
  }

  public String cartHash(OrderDraft draft, String customerId) {
    try {
      var stableLines = draft.lineItems().stream()
          .sorted(Comparator.comparing(line -> line.dishId()))
          .map(line -> Map.of(
              "dishId", line.dishId(),
              "quantity", line.quantity(),
              "unitAmount", line.unitAmount()))
          .toList();
      Map<String, Object> checkout = new LinkedHashMap<>();
      checkout.put("customerId", customerId);
      checkout.put("amount", draft.amount());
      checkout.put("currency", draft.currency());
      checkout.put("lineItems", stableLines);
      byte[] json = mapper.writeValueAsBytes(checkout);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
    } catch (JacksonException | NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Unable to hash checkout", exception);
    }
  }

  public String newOrderId() {
    return "ord_" + UUID.randomUUID();
  }
}

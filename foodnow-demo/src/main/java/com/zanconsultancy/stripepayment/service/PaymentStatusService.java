package com.zanconsultancy.stripepayment.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class PaymentStatusService {
  public Presentation present(String status) {
    return switch (status) {
      case "succeeded" -> new Presentation("Payment successful", "Your payment was received. Your order is ready for fulfillment.", "success", 200);
      case "processing" -> new Presentation("Payment processing", "Your payment is still processing. Please check back shortly.", "info", 202);
      case "requires_payment_method" -> new Presentation("Payment failed", "Your payment was not completed. Please return to checkout and try again.", "danger", 402);
      case "canceled" -> new Presentation("Payment canceled", "This payment was canceled. Please return to checkout to start again.", "warning", 409);
      default -> new Presentation("Payment pending", "Your payment has not completed yet.", "info", 202);
    };
  }
  public String money(long minor, String currency) {
    NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
    format.setCurrency(Currency.getInstance(currency.toUpperCase(Locale.ROOT)));
    return format.format(BigDecimal.valueOf(minor, 2));
  }
  public record Presentation(String title, String message, String style, int httpStatus) {}
}

package com.zanconsultancy.stripepayment;

import static org.junit.jupiter.api.Assertions.*;

import com.zanconsultancy.stripepayment.config.StripeProperties;
import com.zanconsultancy.stripepayment.service.StripeCustomerService;
import org.junit.jupiter.api.Test;

class StripeCustomerServiceTests {
  private final StripeCustomerService customers = new StripeCustomerService(
      new StripeProperties("pk_test_example", "sk_test_example", "whsec_example"));

  @Test
  void normalizesValidEmail() {
    assertEquals("customer@example.com", customers.normalizeEmail("  Customer@Example.COM  "));
  }

  @Test
  void rejectsInvalidEmails() {
    assertNull(customers.normalizeEmail("not-an-email"));
    assertNull(customers.normalizeEmail(""));
    assertNull(customers.normalizeEmail(null));
  }

  @Test
  void recognizesTestMode() {
    assertTrue(customers.testMode());
    StripeCustomerService live = new StripeCustomerService(
        new StripeProperties("pk_live_example", "sk_live_example", "whsec_example"));
    assertFalse(live.testMode());
  }
}

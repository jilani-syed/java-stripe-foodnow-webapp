package com.zanconsultancy.stripepayment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "stripe.publishable-key=pk_test_example",
    "stripe.secret-key=sk_test_example",
    "stripe.webhook-secret=whsec_example",
    "app.order-store-path=${java.io.tmpdir}/stripe-press-context-orders.json"
})
class StripePaymentApplicationTests {

  @Test
  void contextLoads() {
  }
}

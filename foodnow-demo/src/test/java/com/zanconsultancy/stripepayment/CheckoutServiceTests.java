package com.zanconsultancy.stripepayment;

import static org.junit.jupiter.api.Assertions.*;
import tools.jackson.databind.ObjectMapper;
import com.zanconsultancy.stripepayment.model.*;
import com.zanconsultancy.stripepayment.service.CheckoutService;
import java.util.List;
import org.junit.jupiter.api.Test;

class CheckoutServiceTests {
  private final CheckoutService checkout = new CheckoutService(new ObjectMapper());

  @Test
  void validatesUuidV4() {
    assertDoesNotThrow(() -> checkout.validateToken("550e8400-e29b-41d4-a716-446655440000"));
    assertThrows(IllegalArgumentException.class, () -> checkout.validateToken("not-a-token"));
  }

  @Test
  void hashIsStableAcrossLineOrder() {
    OrderLine one = new OrderLine("1", "One", 2, 2300, 4600);
    OrderLine two = new OrderLine("2", "Two", 1, 2500, 2500);
    assertEquals(
        checkout.cartHash(new OrderDraft(List.of(one, two), 3, 7100, "usd"), "cus_one"),
        checkout.cartHash(new OrderDraft(List.of(two, one), 3, 7100, "usd"), "cus_one"));
  }

  @Test
  void hashIncludesCustomerId() {
    OrderLine one = new OrderLine("1", "One", 1, 2300, 2300);
    OrderDraft draft = new OrderDraft(List.of(one), 1, 2300, "usd");
    assertNotEquals(checkout.cartHash(draft, "cus_one"), checkout.cartHash(draft, "cus_two"));
  }
}

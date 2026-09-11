package com.zanconsultancy.stripepayment;

import static org.junit.jupiter.api.Assertions.*;
import com.zanconsultancy.stripepayment.model.CartItem;
import com.zanconsultancy.stripepayment.service.CatalogService;
import com.zanconsultancy.stripepayment.service.OrderCalculator;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderCalculatorTests {
  private final OrderCalculator calculator = new OrderCalculator(new CatalogService());
  @Test void calculatesAuthoritativeTotal() {
    var order = calculator.calculate(List.of(new CartItem("1", 2), new CartItem("4", 1)));
    assertEquals(5598, order.amount()); assertEquals(3, order.itemCount()); assertEquals("usd", order.currency());
  }
  @Test void rejectsMixedRestaurants() { assertThrows(IllegalArgumentException.class, () -> calculator.calculate(List.of(new CartItem("1",1),new CartItem("2",1)))); }
  @Test void rejectsEmptyUnknownInvalidAndDuplicateItems() {
    assertThrows(IllegalArgumentException.class, () -> calculator.calculate(List.of()));
    assertThrows(IllegalArgumentException.class, () -> calculator.calculate(List.of(new CartItem("99", 1))));
    assertThrows(IllegalArgumentException.class, () -> calculator.calculate(List.of(new CartItem("1", 11))));
    assertThrows(IllegalArgumentException.class, () -> calculator.calculate(List.of(new CartItem("1", 1), new CartItem("1", 2))));
  }
}

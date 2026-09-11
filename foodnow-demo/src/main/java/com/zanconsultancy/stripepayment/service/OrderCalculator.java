package com.zanconsultancy.stripepayment.service;

import com.zanconsultancy.stripepayment.model.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class OrderCalculator {
  private final CatalogService catalog;
  public OrderCalculator(CatalogService catalog) { this.catalog = catalog; }
  public OrderDraft calculate(List<CartItem> cart) {
    if (cart == null || cart.isEmpty()) throw new IllegalArgumentException("Cart cannot be empty");
    Set<String> seen = new HashSet<>();
    List<OrderLine> lines = cart.stream().map(item -> {
      if (item == null || item.dishId() == null || item.dishId().isBlank()) throw new IllegalArgumentException("Invalid cart item");
      if (item.quantity() < 1 || item.quantity() > 10) throw new IllegalArgumentException("Quantity must be between 1 and 10");
      if (!seen.add(item.dishId())) throw new IllegalArgumentException("Duplicate dish in cart: " + item.dishId());
      Dish dish = catalog.require(item.dishId());
      return new OrderLine(dish.id(), dish.title(), item.quantity(), dish.amount(), Math.multiplyExact(dish.amount(), item.quantity()));
    }).toList();
    if (lines.stream().map(l -> catalog.require(l.dishId()).author()).distinct().count() != 1)
      throw new IllegalArgumentException("Choose dishes from one restaurant per order");
    int count = lines.stream().mapToInt(OrderLine::quantity).sum();
    long amount = lines.stream().mapToLong(OrderLine::lineTotal).sum();
    return new OrderDraft(lines, count, amount + 399 + 199, "usd");
  }
}

package com.zanconsultancy.stripepayment.model;
import java.util.List;
public record OrderDraft(List<OrderLine> lineItems, int itemCount, long amount, String currency) {}

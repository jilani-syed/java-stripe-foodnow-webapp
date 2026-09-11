package com.zanconsultancy.stripepayment.model;
public record OrderLine(String dishId, String title, int quantity, long unitAmount, long lineTotal) {}

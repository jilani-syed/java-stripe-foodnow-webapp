package com.zanconsultancy.stripepayment.service;
public class CartChangedException extends RuntimeException {
  public CartChangedException() { super("The cart changed during checkout. Start a new checkout attempt."); }
}

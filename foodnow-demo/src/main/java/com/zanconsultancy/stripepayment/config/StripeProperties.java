package com.zanconsultancy.stripepayment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stripe")
public record StripeProperties(String publishableKey, String secretKey, String webhookSecret) {
  public boolean liveMode() { return secretKey != null && secretKey.startsWith("sk_live_"); }
}

package com.zanconsultancy.stripepayment.config;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
@Component
public class StartupValidator {
 public StartupValidator(StripeProperties s, Environment env) {
  String mode=env.getProperty("foodnow.mode", "rehearsal");
  if (!mode.equals("rehearsal") && !mode.equals("stripe")) throw new IllegalStateException("FOODNOW_MODE must be rehearsal or stripe");
  if(s.liveMode() || (s.publishableKey()!=null && s.publishableKey().startsWith("pk_live_"))) throw new IllegalStateException("FoodNow accepts test keys only");
  if(mode.equals("stripe") && (!s.secretKey().startsWith("sk_test_") || !s.publishableKey().startsWith("pk_test_") || !s.webhookSecret().startsWith("whsec_"))) throw new IllegalStateException("Stripe mode requires test keys and a webhook signing secret");
 }
}

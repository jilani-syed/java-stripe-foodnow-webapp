package com.zanconsultancy.stripepayment;

import com.zanconsultancy.stripepayment.config.AppProperties;
import com.zanconsultancy.stripepayment.config.StripeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({StripeProperties.class, AppProperties.class})
public class StripePaymentApplication {

  public static void main(String[] args) {
    SpringApplication.run(StripePaymentApplication.class, args);
  }
}

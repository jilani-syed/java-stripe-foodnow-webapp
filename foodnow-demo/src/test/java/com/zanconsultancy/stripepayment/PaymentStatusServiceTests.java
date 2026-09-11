package com.zanconsultancy.stripepayment;
import static org.junit.jupiter.api.Assertions.*;
import com.zanconsultancy.stripepayment.service.PaymentStatusService;
import org.junit.jupiter.api.Test;
class PaymentStatusServiceTests {
  private final PaymentStatusService service = new PaymentStatusService();
  @Test void mapsStatusesAndFormatsCurrency() {
    assertEquals(200, service.present("succeeded").httpStatus());
    assertEquals(402, service.present("requires_payment_method").httpStatus());
    assertEquals("$23.00", service.money(2300, "usd"));
  }
}

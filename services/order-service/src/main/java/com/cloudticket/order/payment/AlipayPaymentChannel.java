package com.cloudticket.order.payment;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AlipayPaymentChannel implements PaymentChannel {

  public static final String METHOD = "ALIPAY";

  @Override
  public String method() {
    return METHOD;
  }

  @Override
  public String provider() {
    return "alipay";
  }

  @Override
  public String newTransactionId() {
    return "ALI-" + UUID.randomUUID();
  }
}

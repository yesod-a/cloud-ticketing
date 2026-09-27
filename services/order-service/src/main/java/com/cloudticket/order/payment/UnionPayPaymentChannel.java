package com.cloudticket.order.payment;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UnionPayPaymentChannel implements PaymentChannel {

  public static final String METHOD = "UNIONPAY";

  @Override
  public String method() {
    return METHOD;
  }

  @Override
  public String provider() {
    return "unionpay";
  }

  @Override
  public String newTransactionId() {
    return "UP-" + UUID.randomUUID();
  }
}

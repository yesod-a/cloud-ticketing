package com.cloudticket.order.payment;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WeChatPaymentChannel implements PaymentChannel {

  public static final String METHOD = "WECHAT";

  @Override
  public String method() {
    return METHOD;
  }

  @Override
  public String provider() {
    return "wechat";
  }

  @Override
  public String newTransactionId() {
    return "WX-" + UUID.randomUUID();
  }
}

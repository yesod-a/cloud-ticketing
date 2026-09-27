package com.cloudticket.order.payment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Resolves the channel for a requested method.
 *
 * <p>The registered channels form the allow-list, so an unknown or blank method falls back to
 * WeChat exactly like the previous hard-coded {@code Set} did — but adding a channel is now a new
 * bean rather than an edit to this class.
 */
@Component
public class PaymentChannelRegistry {

  private final Map<String, PaymentChannel> channels = new LinkedHashMap<>();
  private final PaymentChannel fallback;

  public PaymentChannelRegistry(List<PaymentChannel> available, WeChatPaymentChannel weChat) {
    for (PaymentChannel channel : available) {
      channels.put(channel.method().toUpperCase(Locale.ROOT), channel);
    }
    this.fallback = channels.getOrDefault(WeChatPaymentChannel.METHOD, weChat);
  }

  public PaymentChannel resolve(String requestedMethod) {
    String value = requestedMethod == null ? "" : requestedMethod.trim().toUpperCase(Locale.ROOT);
    return channels.getOrDefault(value, fallback);
  }

  public List<String> methods() {
    return List.copyOf(channels.keySet());
  }
}

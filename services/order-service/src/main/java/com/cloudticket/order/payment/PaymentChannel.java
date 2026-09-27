package com.cloudticket.order.payment;

/**
 * One selectable payment channel.
 *
 * <p>Today the simulated channels only differ in how they identify themselves: the QR payload, the
 * provider slug and the reference handed back when the payment settles. The abstraction exists
 * because these are exactly the points where a real integration diverges — callback body format,
 * signature verification, refund endpoint and reconciliation file all belong behind this interface
 * instead of growing another branch in {@code PaymentService}.
 */
public interface PaymentChannel {

  /** Method code accepted by the API and stored on the payment row, for example {@code WECHAT}. */
  String method();

  /** Provider slug used in emitted events, for example {@code wechat}. */
  String provider();

  /** Reference returned by the provider once the payment settles. */
  String newTransactionId();

  /** Payload the client renders as a QR code to start a payment for this channel. */
  default String qrContent(String orderId, String qrToken) {
    return "cloudticket://pay?order=" + orderId + "&method=" + method() + "&token=" + qrToken;
  }
}

package com.cloudticket.order.event;

/** Body of the {@code PaymentSucceeded} event, matching {@code docs/contracts/payment-events-v1.md}. */
public record PaymentSucceededPayload(String orderId, String paymentId, int amountMinor, String currency,
                                      String provider, String providerTransactionId) {}

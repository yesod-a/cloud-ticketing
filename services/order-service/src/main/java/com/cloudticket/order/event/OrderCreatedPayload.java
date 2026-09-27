package com.cloudticket.order.event;

import java.time.Instant;

/** Body of the {@code OrderCreated} event. */
public record OrderCreatedPayload(String orderId, String userId, String sessionId, String seatIds,
                                  String status, int amountMinor, Instant createdAt) {}

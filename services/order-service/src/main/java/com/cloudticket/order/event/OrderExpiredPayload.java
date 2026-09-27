package com.cloudticket.order.event;

/** Body of the {@code OrderExpired} event: enough for inventory to release the held seats. */
public record OrderExpiredPayload(String orderId, String userId, String sessionId, String seatIds) {}

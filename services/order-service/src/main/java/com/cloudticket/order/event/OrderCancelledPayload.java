package com.cloudticket.order.event;

/** Body of the {@code OrderCancelled} event. */
public record OrderCancelledPayload(String orderId, String status) {}

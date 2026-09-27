package com.cloudticket.order.event;

/** Body of the {@code OrderRefunded} event. */
public record OrderRefundedPayload(String orderId, String refundId, String reviewedBy, String status) {}

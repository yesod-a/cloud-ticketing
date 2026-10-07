package com.cloudticket.common.events;

public final class EventTypes {
    private EventTypes() {}
    public static final String ORDER_CREATED = "OrderCreated";
    public static final String ORDER_CANCELLED = "OrderCancelled";
    public static final String ORDER_REFUNDED = "OrderRefunded";
    public static final String ORDER_EXPIRED = "OrderExpired";
    public static final String PAYMENT_SUCCEEDED = "PaymentSucceeded";
    public static final String PAYMENT_FAILED = "PaymentFailed";
    public static final String INVENTORY_LOCKED = "InventoryLocked";
    public static final String INVENTORY_RELEASED = "InventoryReleased";
    public static final String INVENTORY_HELD = "InventoryHeld";
    public static final String RESERVE_TICKET_COMMAND = "ReserveTicketCommand";
    public static final String COUPON_CONSUMED = "CouponConsumed";
    public static final String COUPON_RELEASED = "CouponReleased";
    public static final String COUPON_RESTORED = "CouponRestored";
    public static final String COMMENT_LIKE_COUNT_CHANGED = "CommentLikeCountChanged";
}

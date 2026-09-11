package com.cloudticket.common.events;

public final class EventTypes {
    private EventTypes() {}
    public static final String ORDER_CREATED = "OrderCreated";
    public static final String ORDER_EXPIRED = "OrderExpired";
    public static final String INVENTORY_LOCKED = "InventoryLocked";
    public static final String INVENTORY_RELEASED = "InventoryReleased";
}

package com.cloudticket.inventory.redis;

/** Lifecycle states shared by Redis admission, Kafka publication and reconciliation. */
public enum QueuedReservationStatus {
  PENDING_PUBLISH, PUBLISHING, PUBLISHED, INVENTORY_HELD, ORDER_CREATED,
  PAID, RELEASED, FAILED
}

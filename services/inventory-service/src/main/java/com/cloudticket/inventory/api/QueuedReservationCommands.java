package com.cloudticket.inventory.api;

import java.util.List;

public final class QueuedReservationCommands {
  private QueuedReservationCommands() {}
  public record Reserve(String reservationId, String userId, String sessionId, String mode,
                        List<String> seatIds, Integer quantity, Integer capacity,
                        String idempotencyKey, Long ttlSeconds) {
    public long ttlOrDefault() { return ttlSeconds == null ? 900L : ttlSeconds; }
    public int quantityOrZero() { return quantity == null ? 0 : quantity; }
    public String modeOrDefault() { return mode == null || mode.isBlank() ? "GRID" : mode.trim().toUpperCase(); }
  }
}

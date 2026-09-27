package com.cloudticket.inventory.api;

import com.cloudticket.inventory.persistence.InventorySeatRepository;
import com.cloudticket.inventory.persistence.entity.InventorySeatEntity;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/** Typed request bodies for the inventory API. */
public final class InventoryCommands {

  private InventoryCommands() {}

  /** Seat state change; the reason is mandatory and lands in the audit log. */
  public record AdjustSeat(String status, String reason) {
    public String statusOrDefault() {
      return status == null || status.isBlank() ? InventorySeatRepository.AVAILABLE : status.trim();
    }
  }

  /**
   * Lock request from order-service. {@code seatIds} is declared as {@code Object} because the
   * endpoint has always accepted either a JSON array or a comma separated string.
   */
  public record ReserveLocks(String orderId, String sessionId, Object seatIds, Long ttlSeconds) {
    private static final long DEFAULT_TTL_SECONDS = 900;

    public List<String> seatIdsAsList() {
      if (seatIds instanceof List<?> values) return values.stream().map(String::valueOf).toList();
      String raw = seatIds == null ? "" : String.valueOf(seatIds);
      return Arrays.stream(raw.split(",")).map(String::trim).filter(value -> !value.isBlank()).toList();
    }

    public long ttlOrDefault() {
      return ttlSeconds == null ? DEFAULT_TTL_SECONDS : ttlSeconds;
    }
  }

  /** Seat layout pushed by activity-service when a session is created. */
  public record ProvisionSeats(String activityId, List<ProvisionSeat> seats) {

    public List<InventorySeatEntity> toEntities(String sessionId) {
      if (seats == null) return List.of();
      return seats.stream().map(seat -> seat.toEntity(sessionId, activityId)).toList();
    }

    public record ProvisionSeat(String id, String areaLabel, String rowLabel, Integer seatNumber,
                                String displayName, String seatType, Integer x, Integer y, String status) {

      private InventorySeatEntity toEntity(String sessionId, String activityId) {
        return InventorySeatRepository.seat(id, sessionId, activityId, areaLabel, rowLabel,
            seatNumber == null ? 0 : seatNumber, displayName, seatType,
            x == null ? null : BigDecimal.valueOf(x), y == null ? null : BigDecimal.valueOf(y),
            status == null || status.isBlank() ? InventorySeatRepository.AVAILABLE : status);
      }
    }
  }
}

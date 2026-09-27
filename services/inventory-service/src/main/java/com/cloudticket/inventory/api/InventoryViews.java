package com.cloudticket.inventory.api;

import com.cloudticket.inventory.persistence.entity.InventorySeatEntity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Renders inventory rows as the JSON the admin console and the public seat picker expect. */
public final class InventoryViews {

  private InventoryViews() {}

  public static Map<String, Object> adminSeat(InventorySeatEntity seat) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", seat.getId());
    value.put("sessionId", seat.getSessionId());
    value.put("row", seat.getRowLabel());
    value.put("number", seat.getSeatNumber() == null ? 0 : seat.getSeatNumber());
    value.put("status", seat.getStatus());
    value.put("updatedAt", iso(seat.getUpdatedAt()));
    return value;
  }

  public static Map<String, Object> publicSeat(InventorySeatEntity seat) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", seat.getId());
    value.put("areaLabel", seat.getAreaLabel());
    value.put("row", seat.getRowLabel());
    value.put("number", seat.getSeatNumber() == null ? 0 : seat.getSeatNumber());
    value.put("displayName", seat.getDisplayName());
    value.put("x", whole(seat.getPositionX()));
    value.put("y", whole(seat.getPositionY()));
    value.put("type", seat.getSeatType());
    value.put("status", seat.getStatus());
    return value;
  }

  private static int whole(java.math.BigDecimal value) {
    return value == null ? 0 : value.intValue();
  }

  private static String iso(Instant value) {
    return value == null ? null : value.toString();
  }
}

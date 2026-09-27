package com.cloudticket.inventory.persistence;

import com.cloudticket.inventory.persistence.entity.InventorySeatEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Assigns immutable per-session indexes across layout replacements. */
public final class SeatIndexAllocator {

  private SeatIndexAllocator() {}

  public static void assign(List<InventorySeatEntity> existing, List<InventorySeatEntity> replacement) {
    Map<String, Integer> known = new HashMap<>();
    int next = 0;
    for (InventorySeatEntity seat : existing) {
      if (seat.getId() != null && seat.getSeatIndex() != null) {
        known.put(seat.getId(), seat.getSeatIndex());
        next = Math.max(next, seat.getSeatIndex() + 1);
      }
    }
    for (InventorySeatEntity seat : replacement) {
      Integer old = known.get(seat.getId());
      seat.setSeatIndex(old == null ? next++ : old);
    }
  }
}

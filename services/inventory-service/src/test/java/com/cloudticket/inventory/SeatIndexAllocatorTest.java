package com.cloudticket.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cloudticket.inventory.persistence.entity.InventorySeatEntity;
import com.cloudticket.inventory.persistence.SeatIndexAllocator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SeatIndexAllocatorTest {

  @Test
  void preservesPublishedIndexesAndAppendsNewSeatsAfterHistoricalMaximum() {
    var existing = List.of(seat("a", 0), seat("b", 5));
    var replacement = List.of(seat("new", null), seat("b", null), seat("a", null));

    SeatIndexAllocator.assign(existing, replacement);

    assertEquals(List.of(6, 5, 0), replacement.stream().map(InventorySeatEntity::getSeatIndex).toList());
  }

  @Test
  void initializesIndexesForFirstProvisionInPayloadOrder() {
    var replacement = List.of(seat("a", null), seat("b", null));

    SeatIndexAllocator.assign(List.of(), replacement);

    assertEquals(List.of(0, 1), replacement.stream().map(InventorySeatEntity::getSeatIndex).toList());
  }

  private static InventorySeatEntity seat(String id, Integer index) {
    var seat = new InventorySeatEntity();
    seat.setId(id);
    seat.setSeatIndex(index);
    return seat;
  }
}

package com.cloudticket.inventory;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloudticket.inventory.service.InventoryReservationService;
import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryReservationServiceTest {
  @Test
  void rejectsDuplicateSeatIdsBeforeReservation() {
    assertThrows(IllegalArgumentException.class, () ->
        InventoryReservationService.normalizeSeatIds(List.of("seat-1", "seat-1")));
  }
}

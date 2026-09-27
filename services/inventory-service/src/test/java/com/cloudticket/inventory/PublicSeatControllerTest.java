package com.cloudticket.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cloudticket.inventory.api.PublicSeatController;
import com.cloudticket.inventory.cache.SeatBitmapProjection;
import com.cloudticket.inventory.persistence.InventorySeatRepository;
import com.cloudticket.inventory.persistence.entity.InventorySeatEntity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PublicSeatControllerTest {

  private final InventorySeatRepository seats = mock(InventorySeatRepository.class);
  private final SeatBitmapProjection projection = mock(SeatBitmapProjection.class);

  @Test
  void publicReadKeepsResponseShapeAndUsesProjectionOverlay() {
    InventorySeatEntity source = InventorySeatRepository.seat("seat-1", "session-1", "activity-1",
        "Main", "A", 1, "A-1", "REGULAR", null, null, "AVAILABLE");
    InventorySeatEntity projected = InventorySeatRepository.seat("seat-1", "session-1", "activity-1",
        "Main", "A", 1, "A-1", "REGULAR", null, null, "SOLD");
    when(seats.listBySession("session-1")).thenReturn(List.of(source));
    when(projection.overlay("session-1", List.of(source))).thenReturn(List.of(projected));

    Map<String, Object> response = new PublicSeatController(seats, projection).seats("session-1");

    assertEquals("OK", response.get("code"));
    List<?> data = (List<?>) response.get("data");
    assertEquals("seat-1", ((Map<?, ?>) data.get(0)).get("id"));
    assertEquals("SOLD", ((Map<?, ?>) data.get(0)).get("status"));
    assertEquals("A-1", ((Map<?, ?>) data.get(0)).get("displayName"));
  }
}

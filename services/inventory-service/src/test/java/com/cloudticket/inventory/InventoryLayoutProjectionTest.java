package com.cloudticket.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.cloudticket.inventory.cache.InventoryLayoutProjection;
import com.cloudticket.inventory.persistence.entity.InventorySeatEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class InventoryLayoutProjectionTest {

  private final InventoryLayoutProjection projection = new InventoryLayoutProjection(
      mock(StringRedisTemplate.class), new ObjectMapper());

  @Test
  void snapshotSerializesLayoutAndStateBitmapsBySeatIndex() {
    InventorySeatEntity sold = seat("seat-1", 0, "SOLD");
    InventorySeatEntity available = seat("seat-2", 1, "AVAILABLE");

    var snapshot = projection.snapshot("session-1", List.of(sold, available), "v7");

    assertEquals("v7", snapshot.version());
    assertTrue(snapshot.layoutJson().contains("seat-1"));
    assertTrue(bit(snapshot.soldBitmap(), 0));
    assertTrue(!bit(snapshot.soldBitmap(), 1));
  }

  private static InventorySeatEntity seat(String id, int index, String status) {
    InventorySeatEntity seat = new InventorySeatEntity();
    seat.setId(id);
    seat.setSessionId("session-1");
    seat.setSeatIndex(index);
    seat.setRowLabel("A");
    seat.setSeatNumber(index + 1);
    seat.setStatus(status);
    return seat;
  }

  private static boolean bit(byte[] bytes, int index) {
    return (bytes[index / 8] & (1 << (7 - index % 8))) != 0;
  }
}

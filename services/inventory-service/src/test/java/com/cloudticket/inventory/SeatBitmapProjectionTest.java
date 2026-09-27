package com.cloudticket.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.inventory.cache.SeatBitmapProjection;
import com.cloudticket.inventory.persistence.entity.InventorySeatEntity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.connection.RedisConnection;

class SeatBitmapProjectionTest {

  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  @SuppressWarnings("unchecked")
  private final ValueOperations<String, String> values = mock(ValueOperations.class);

  @Test
  void overlaysStatusesFromReadyBitmaps() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.get("cloudticket:inventory:{s-1}:version")).thenReturn("ready");
    when(redis.execute(org.mockito.ArgumentMatchers.<org.springframework.data.redis.core.RedisCallback<byte[]>>any()))
        .thenReturn(new byte[] {(byte) 0x40}, new byte[] {(byte) 0x80}, new byte[] {0});

    var source = List.of(seat("a", "AVAILABLE"), seat("b", "AVAILABLE"));
    source.get(1).setSeatIndex(1);
    var result = new SeatBitmapProjection(redis, true).overlay("s-1", source);

    assertEquals("LOCKED", result.get(0).getStatus());
    assertEquals("SOLD", result.get(1).getStatus());
    assertEquals("a", result.get(0).getId());
  }

  @Test
  void fallsBackToDatabaseRowsWhenProjectionIsNotReady() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.get("cloudticket:inventory:{s-1}:version")).thenReturn(null);
    var source = List.of(seat("a", "AVAILABLE"));

    var result = new SeatBitmapProjection(redis, true).overlay("s-1", source);

    assertSame(source, result);
    verify(redis, never()).execute(org.mockito.ArgumentMatchers.<org.springframework.data.redis.core.RedisCallback<byte[]>>any());
  }

  @Test
  void disabledProjectionAlwaysUsesDatabaseRows() {
    var source = List.of(seat("a", "AVAILABLE"));

    var result = new SeatBitmapProjection(redis, false).overlay("s-1", source);

    assertSame(source, result);
    verify(redis, never()).opsForValue();
  }

  @Test
  void rebuildWritesAllStatusBitmapsAndVersion() {
    when(redis.opsForValue()).thenReturn(values);
    var source = List.of(seat("a", "AVAILABLE"), seat("b", "SOLD"), seat("c", "LOCKED"), seat("d", "DISABLED"));

    new SeatBitmapProjection(redis, true).rebuild("s-1", source);

    verify(redis).delete(List.of(
        "cloudticket:inventory:{s-1}:sold",
        "cloudticket:inventory:{s-1}:locked",
        "cloudticket:inventory:{s-1}:disabled",
        "cloudticket:inventory:{s-1}:version"));
    verify(values).set(eq("cloudticket:inventory:{s-1}:version"), eq("ready"));
  }

  private static InventorySeatEntity seat(String id, String status) {
    return InventorySeatEntityFactory.seat(id, "s-1", status);
  }

  private static final class InventorySeatEntityFactory {
    private static InventorySeatEntity seat(String id, String sessionId, String status) {
      var value = new InventorySeatEntity();
      value.setId(id);
    value.setSessionId(sessionId);
      value.setSeatIndex(status.equals("AVAILABLE") ? 0 : status.equals("SOLD") ? 1 : status.equals("LOCKED") ? 2 : 3);
      value.setStatus(status);
      return value;
    }
  }
}

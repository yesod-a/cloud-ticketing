package com.cloudticket.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.inventory.cache.InventoryLayoutProjection;
import com.cloudticket.inventory.cache.InventoryReadService;
import com.cloudticket.inventory.persistence.InventorySeatRepository;
import com.cloudticket.inventory.persistence.entity.InventorySeatEntity;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class InventoryReadServiceTest {

  private final InventorySeatRepository seats = mock(InventorySeatRepository.class);
  private final InventoryLayoutProjection projection = mock(InventoryLayoutProjection.class);
  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  private final ValueOperations<String, String> values = mock(ValueOperations.class);
  private final InventoryReadService service = new InventoryReadService(seats, projection, redis);

  @Test
  void readyRedisProjectionAvoidsMySqlSeatListQuery() {
    var snapshot = new InventoryLayoutProjection.ProjectionSnapshot("v1", "[]", new byte[0], new byte[0], new byte[0]);
    when(projection.readReady("session-1")).thenReturn(Optional.of(snapshot));
    when(projection.toEntities("session-1", snapshot)).thenReturn(List.of());

    assertEquals(List.of(), service.seats("session-1"));

    verify(seats, never()).listBySession("session-1");
  }

  @Test
  void cacheMissHasOneRebuilderAndWritesAReadyProjection() {
    InventorySeatEntity row = new InventorySeatEntity();
    row.setSeatIndex(0);
    when(projection.readReady("session-1")).thenReturn(Optional.empty());
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfAbsent(any(), eq("session-1"), any(Duration.class))).thenReturn(true);
    when(seats.listBySession("session-1")).thenReturn(List.of(row));
    when(projection.snapshot(eq("session-1"), any(), any())).thenReturn(
        new InventoryLayoutProjection.ProjectionSnapshot("v1", "[]", new byte[1], new byte[1], new byte[1]));
    when(projection.toEntities(eq("session-1"), any())).thenReturn(List.of(row));

    assertEquals(1, service.seats("session-1").size());

    verify(projection).writeVersioned(eq("session-1"), any());
  }

  @Test
  void nonOwnerChecksRedisAgainBeforeFallingBackToMySql() {
    var snapshot = new InventoryLayoutProjection.ProjectionSnapshot("v2", "[]", new byte[0], new byte[0], new byte[0]);
    when(projection.readReady("session-1")).thenReturn(Optional.empty(), Optional.of(snapshot));
    when(projection.toEntities("session-1", snapshot)).thenReturn(List.of());
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfAbsent(any(), eq("session-1"), any(Duration.class))).thenReturn(false);

    assertEquals(List.of(), service.seats("session-1"));

    verify(seats, never()).listBySession("session-1");
  }
}

package com.cloudticket.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.inventory.cache.InventoryLayoutProjection;
import com.cloudticket.inventory.persistence.InventoryCacheWarmupRepository;
import com.cloudticket.inventory.persistence.InventorySeatRepository;
import com.cloudticket.inventory.persistence.entity.InventoryCacheWarmupEntity;
import com.cloudticket.inventory.persistence.entity.InventorySeatEntity;
import com.cloudticket.inventory.reconcile.InventoryCachePreheatService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class InventoryCachePreheatServiceTest {
  private final InventoryCacheWarmupRepository warmups = mock(InventoryCacheWarmupRepository.class);
  private final InventorySeatRepository seats = mock(InventorySeatRepository.class);
  private final InventoryLayoutProjection projection = mock(InventoryLayoutProjection.class);
  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  private final ValueOperations<String, String> values = mock(ValueOperations.class);
  private final Executor direct = Runnable::run;
  private final Clock clock = Clock.fixed(Instant.parse("2026-12-01T11:30:00Z"), ZoneOffset.UTC);
  private final InventoryCachePreheatService service =
      new InventoryCachePreheatService(warmups, seats, projection, redis, direct, clock, 60, 5);

  @Test
  void marksTheRuntimeConstructorForSpringInjection() {
    long autowiredConstructors = Arrays.stream(InventoryCachePreheatService.class.getDeclaredConstructors())
        .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
        .count();

    assertEquals(1, autowiredConstructors);
  }

  @Test
  void dueRowsAreClaimedAndBuiltIntoReadyProjection() {
    InventoryCacheWarmupEntity row = new InventoryCacheWarmupEntity();
    row.setSessionId("session-1");
    when(warmups.claimDue(eq("job-1"), any(), any(), eq(5))).thenReturn(List.of(row));
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfAbsent(any(), eq("job-1"), any())).thenReturn(true);
    InventorySeatEntity seat = new InventorySeatEntity();
    seat.setSeatIndex(0);
    when(seats.listBySession("session-1")).thenReturn(List.of(seat));
    var snapshot = new InventoryLayoutProjection.ProjectionSnapshot("v-1", "[]", new byte[1], new byte[1], new byte[1]);
    when(projection.snapshot(eq("session-1"), any(), any())).thenReturn(snapshot);

    assertEquals(1, service.preheatDue("job-1", 5));

    verify(projection).writeVersioned("session-1", snapshot);
    verify(warmups).markReady(eq("session-1"), eq("job-1"), argThat(value -> value.startsWith("v-")),
        eq(Instant.parse("2026-12-01T11:30:00Z")));
  }

  @Test
  void anotherRebuilderDoesNotReadMySql() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfAbsent(any(), eq("job-1"), any())).thenReturn(false);

    service.preheatSession("session-1", "job-1");

    verify(seats, never()).listBySession("session-1");
    verify(warmups, never()).markReady(any(), any(), any(), any());
  }
}

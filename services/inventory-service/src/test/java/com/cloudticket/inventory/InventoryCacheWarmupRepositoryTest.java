package com.cloudticket.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.inventory.persistence.InventoryCacheWarmupRepository;
import com.cloudticket.inventory.persistence.entity.InventoryCacheWarmupEntity;
import com.cloudticket.inventory.persistence.mapper.InventoryCacheWarmupMapper;
import java.time.Instant;
import java.lang.reflect.Constructor;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryCacheWarmupRepositoryTest {

  private final InventoryCacheWarmupMapper mapper = mock(InventoryCacheWarmupMapper.class);
  private final InventoryCacheWarmupRepository repository = new InventoryCacheWarmupRepository(mapper, 35);

  @Test
  void marksThePrimaryConstructorForSpringInjection() {
    Constructor<?>[] constructors = InventoryCacheWarmupRepository.class.getDeclaredConstructors();

    long autowiredConstructors = java.util.Arrays.stream(constructors)
        .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
        .count();

    assertEquals(1, autowiredConstructors);
  }

  @Test
  void upsertSchedulesPreheatBeforeSaleStart() {
    Instant saleStart = Instant.parse("2026-12-01T12:00:00Z");

    repository.upsert("session-1", saleStart);

    verify(mapper).upsert(eq("session-1"), eq(Instant.parse("2026-12-01T11:25:00Z")));
  }

  @Test
  void claimsOnlyRowsWonByConditionalLease() {
    InventoryCacheWarmupEntity first = row("session-1");
    InventoryCacheWarmupEntity second = row("session-2");
    when(mapper.selectDue(any(), eq(10))).thenReturn(List.of(first, second));
    when(mapper.claim(eq("session-1"), any(), any(), any())).thenReturn(1);
    when(mapper.claim(eq("session-2"), any(), any(), any())).thenReturn(0);

    List<InventoryCacheWarmupEntity> claimed = repository.claimDue("worker-1",
        Instant.parse("2026-12-01T12:00:00Z"), Instant.parse("2026-12-01T12:01:00Z"), 10);

    assertEquals(List.of(first), claimed);
  }

  @Test
  void failedAttemptStoresRetryMetadata() {
    repository.markFailed("session-1", "worker-1", "redis unavailable", Instant.parse("2026-12-01T12:05:00Z"));

    verify(mapper).markFailed(eq("session-1"), eq("worker-1"), eq("redis unavailable"),
        eq(Instant.parse("2026-12-01T12:05:00Z")));
    assertTrue(true);
  }

  private static InventoryCacheWarmupEntity row(String sessionId) {
    InventoryCacheWarmupEntity row = new InventoryCacheWarmupEntity();
    row.setSessionId(sessionId);
    return row;
  }
}

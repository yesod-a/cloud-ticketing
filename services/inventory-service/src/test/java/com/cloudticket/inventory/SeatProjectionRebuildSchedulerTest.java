package com.cloudticket.inventory;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.cloudticket.inventory.cache.SeatBitmapProjection;
import com.cloudticket.inventory.persistence.InventoryLockRepository;
import com.cloudticket.inventory.persistence.InventorySeatRepository;
import com.cloudticket.inventory.persistence.entity.InventorySeatEntity;
import com.cloudticket.inventory.redis.RedisSeatLockService;
import com.cloudticket.inventory.reconcile.SeatProjectionRebuildScheduler;
import java.util.List;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SeatProjectionRebuildSchedulerTest {

  private final InventorySeatRepository seats = mock(InventorySeatRepository.class);
  private final SeatBitmapProjection projection = mock(SeatBitmapProjection.class);
  private final InventoryLockRepository locks = mock(InventoryLockRepository.class);
  private final RedisSeatLockService redisLocks = mock(RedisSeatLockService.class);

  @Test
  void rebuildsEverySessionFromMysqlSnapshot() {
    when(seats.sessionIds()).thenReturn(List.of("s-1", "s-2"));
    when(seats.listBySession("s-1")).thenReturn(List.of(new InventorySeatEntity()));
    when(seats.listBySession("s-2")).thenReturn(List.of());
    var scheduler = new SeatProjectionRebuildScheduler(seats, projection, locks, redisLocks, true);

    scheduler.rebuildAll();

    verify(projection).rebuild("s-1", seats.listBySession("s-1"));
    verify(projection).rebuild("s-2", List.of());
  }

  @Test
  void disabledSchedulerDoesNotReadMysql() {
    var scheduler = new SeatProjectionRebuildScheduler(seats, projection, locks, redisLocks, false);

    scheduler.rebuildAll();

    Mockito.verifyNoInteractions(seats, projection);
  }

  @Test
  void restoresMissingRedisHoldFromActiveMysqlLock() {
    when(seats.sessionIds()).thenReturn(List.of("s-1"));
    when(seats.listBySession("s-1")).thenReturn(List.of(seat("seat-1", 7, "LOCKED")));
    when(projection.isReady("s-1")).thenReturn(true);
    when(redisLocks.isEnabled()).thenReturn(true);
    when(locks.activeForSession("s-1")).thenReturn(List.of(lock("seat-1", "order-1", Instant.now().plusSeconds(60))));
    when(redisLocks.holds("s-1")).thenReturn(Map.of());
    when(redisLocks.restoreHoldIfAbsent(org.mockito.ArgumentMatchers.eq("order-1"),
        org.mockito.ArgumentMatchers.eq("s-1"), org.mockito.ArgumentMatchers.eq(7),
        org.mockito.ArgumentMatchers.anyLong()))
        .thenReturn(true);
    var scheduler = new SeatProjectionRebuildScheduler(seats, projection, locks, redisLocks, true);

    scheduler.rebuildAll();

    verify(redisLocks).restoreHoldIfAbsent(org.mockito.ArgumentMatchers.eq("order-1"),
        org.mockito.ArgumentMatchers.eq("s-1"), org.mockito.ArgumentMatchers.eq(7),
        org.mockito.ArgumentMatchers.longThat(value -> value > 0 && value <= 60));
  }

  @Test
  void doesNotDeleteOrphanHoldForLockedSeat() {
    when(seats.sessionIds()).thenReturn(List.of("s-1"));
    when(seats.listBySession("s-1")).thenReturn(List.of(seat("seat-1", 7, "LOCKED")));
    when(projection.isReady("s-1")).thenReturn(true);
    when(redisLocks.isEnabled()).thenReturn(true);
    when(locks.activeForSession("s-1")).thenReturn(List.of());
    when(redisLocks.holds("s-1")).thenReturn(Map.of(7, "order-1:s-1"));
    when(locks.confirmedStillLockedSeatIds("s-1")).thenReturn(List.of("seat-1"));
    var scheduler = new SeatProjectionRebuildScheduler(seats, projection, locks, redisLocks, true);

    scheduler.rebuildAll();

    verify(redisLocks, never()).releaseHoldIfToken("s-1", 7, "order-1:s-1");
  }

  @Test
  void defersOrphanHoldCleanupEvenWhenSeatCurrentlyLooksAvailable() {
    when(seats.sessionIds()).thenReturn(List.of("s-1"));
    when(seats.listBySession("s-1")).thenReturn(List.of(seat("seat-1", 7, "AVAILABLE")));
    when(projection.isReady("s-1")).thenReturn(true);
    when(redisLocks.isEnabled()).thenReturn(true);
    when(locks.activeForSession("s-1")).thenReturn(List.of());
    when(redisLocks.holds("s-1")).thenReturn(Map.of(7, "order-1:s-1"));
    var scheduler = new SeatProjectionRebuildScheduler(seats, projection, locks, redisLocks, true);

    scheduler.rebuildAll();

    verify(redisLocks, never()).releaseHoldIfToken("s-1", 7, "order-1:s-1");
  }

  private static InventorySeatEntity seat(String id, int index, String status) {
    InventorySeatEntity seat = new InventorySeatEntity();
    seat.setId(id);
    seat.setSessionId("s-1");
    seat.setSeatIndex(index);
    seat.setStatus(status);
    return seat;
  }

  private static com.cloudticket.inventory.persistence.entity.InventoryLockEntity lock(
      String seatId, String orderId, Instant expiresAt) {
    var lock = new com.cloudticket.inventory.persistence.entity.InventoryLockEntity();
    lock.setSeatId(seatId);
    lock.setOrderId(orderId);
    lock.setExpiresAt(expiresAt);
    return lock;
  }
}

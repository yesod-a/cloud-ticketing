package com.cloudticket.inventory.reconcile;

import com.cloudticket.inventory.cache.SeatBitmapProjection;
import com.cloudticket.inventory.persistence.InventoryLockRepository;
import com.cloudticket.inventory.persistence.InventorySeatRepository;
import com.cloudticket.inventory.persistence.entity.InventoryLockEntity;
import com.cloudticket.inventory.persistence.entity.InventorySeatEntity;
import com.cloudticket.inventory.redis.RedisSeatLockService;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Rebuilds missing projections from MySQL without touching Redis hold keys. */
@Component
public class SeatProjectionRebuildScheduler {

  private static final Logger log = LoggerFactory.getLogger(SeatProjectionRebuildScheduler.class);

  private final InventorySeatRepository seats;
  private final SeatBitmapProjection projection;
  private final InventoryLockRepository locks;
  private final RedisSeatLockService redisLocks;
  private final boolean enabled;

  public SeatProjectionRebuildScheduler(InventorySeatRepository seats, SeatBitmapProjection projection,
      InventoryLockRepository locks, RedisSeatLockService redisLocks,
      @Value("${cloudticket.inventory.reconciliation.enabled:false}") boolean enabled) {
    this.seats = seats;
    this.projection = projection;
    this.locks = locks;
    this.redisLocks = redisLocks;
    this.enabled = enabled;
  }

  @Scheduled(fixedDelayString = "${cloudticket.inventory.reconciliation.poll-ms:60000}")
  public void rebuildAll() {
    if (!enabled) return;
    for (String sessionId : seats.sessionIds()) {
      var rows = seats.listBySession(sessionId);
      if (!projection.isReady(sessionId)) {
        projection.rebuild(sessionId, rows);
      }
      reconcileHolds(sessionId, rows);
    }
  }

  private void reconcileHolds(String sessionId, java.util.List<InventorySeatEntity> rows) {
    if (!redisLocks.isEnabled()) return;
    Map<String, Integer> indexById = new HashMap<>();
    Map<Integer, InventorySeatEntity> rowByIndex = new HashMap<>();
    for (int offset = 0; offset < rows.size(); offset++) {
      InventorySeatEntity row = rows.get(offset);
      int index = row.getSeatIndex() == null ? offset : row.getSeatIndex();
      indexById.put(row.getId(), index);
      rowByIndex.put(index, row);
    }

    Map<Integer, InventoryLockEntity> activeByIndex = new HashMap<>();
    for (InventoryLockEntity lock : locks.activeForSession(sessionId)) {
      Integer index = indexById.get(lock.getSeatId());
      if (index == null) {
        log.warn("Active inventory lock references a missing seat: session={}, seat={}", sessionId,
            lock.getSeatId());
        continue;
      }
      activeByIndex.put(index, lock);
    }

    Map<Integer, String> redisHolds = redisLocks.holds(sessionId);
    activeByIndex.forEach((index, lock) -> {
      String expected = RedisSeatLockService.token(lock.getOrderId(), sessionId);
      String actual = redisHolds.get(index);
      if (actual == null) {
        long ttl = Math.max(1, Duration.between(Instant.now(), lock.getExpiresAt()).toSeconds());
        if (!redisLocks.restoreHoldIfAbsent(lock.getOrderId(), sessionId, index, ttl)) {
          log.warn("Could not restore missing Redis hold: session={}, seatIndex={}", sessionId, index);
        }
      } else if (!expected.equals(actual)) {
        log.warn("Redis/MySQL hold token mismatch: session={}, seatIndex={}", sessionId, index);
      }
    });

    redisHolds.forEach((index, token) -> {
      if (activeByIndex.containsKey(index)) return;
      InventorySeatEntity row = rowByIndex.get(index);
      if (row == null) {
        log.warn("Redis hold references missing seat index: session={}, seatIndex={}", sessionId, index);
      } else if (InventorySeatRepository.LOCKED.equals(row.getStatus())) {
        log.warn("Locked seat has no active MySQL hold; requires manual reconciliation: session={}, seat={}",
            sessionId, row.getId());
      } else {
        log.warn("Redis hold has no active MySQL lock; deferred for grace-period verification: session={}, seatIndex={}",
            sessionId, index);
      }
    });

    for (String seatId : locks.confirmedStillLockedSeatIds(sessionId)) {
      log.error("Paid/confirmed seat remains LOCKED; manual repair required: session={}, seat={}", sessionId,
          seatId);
    }
  }
}

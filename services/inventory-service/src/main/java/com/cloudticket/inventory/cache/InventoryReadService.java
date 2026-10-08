package com.cloudticket.inventory.cache;

import com.cloudticket.inventory.persistence.InventorySeatRepository;
import com.cloudticket.inventory.persistence.entity.InventorySeatEntity;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Redis-first public inventory reads with a bounded cold-start rebuild. */
@Service
public class InventoryReadService {
  private final InventorySeatRepository seats;
  private final InventoryLayoutProjection projection;
  private final StringRedisTemplate redis;
  private final long waitMillis;

  public InventoryReadService(InventorySeatRepository seats, InventoryLayoutProjection projection,
                              StringRedisTemplate redis) {
    this(seats, projection, redis, 750L);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public InventoryReadService(InventorySeatRepository seats, InventoryLayoutProjection projection,
                              StringRedisTemplate redis,
                              @Value("${cloudticket.inventory.redis-lock.ready-wait-ms:750}") long waitMillis) {
    this.seats = seats;
    this.projection = projection;
    this.redis = redis;
    this.waitMillis = Math.max(50L, Math.min(2_000L, waitMillis));
  }

  public List<InventorySeatEntity> seats(String sessionId) {
    try {
      var ready = tryRebuildOrWait(sessionId);
      if (ready.isPresent()) return overlayHolds(sessionId, projection.toEntities(sessionId, ready.get()));
    } catch (RuntimeException ignored) {
      // A read cache is optional; the durable inventory query remains the fallback.
    }
    return seats.listBySession(sessionId);
  }

  private List<InventorySeatEntity> overlayHolds(String sessionId, List<InventorySeatEntity> rows) {
    if (rows == null || rows.isEmpty()) return rows;
    String prefix = "cloudticket:inventory:" + com.cloudticket.inventory.cache.SeatBitmapProjection.tag(sessionId) + ":hold:";
    Set<Integer> held = redis.execute((org.springframework.data.redis.core.RedisCallback<Set<Integer>>) connection -> {
      Set<Integer> indexes = new HashSet<>();
      try (var cursor = connection.scan(org.springframework.data.redis.core.ScanOptions.scanOptions()
          .match(prefix + "*").count(500).build())) {
        while (cursor.hasNext()) {
          String raw = new String(cursor.next(), StandardCharsets.UTF_8);
          try { indexes.add(Integer.parseInt(raw.substring(prefix.length()))); }
          catch (NumberFormatException ignored) { }
        }
      }
      return indexes;
    });
    if (held == null || held.isEmpty()) return rows;
    for (int offset = 0; offset < rows.size(); offset++) {
      InventorySeatEntity row = rows.get(offset);
      int index = row.getSeatIndex() == null ? offset : row.getSeatIndex();
      if (held.contains(index) && !InventorySeatRepository.SOLD.equals(row.getStatus())) {
        row.setStatus(InventorySeatRepository.LOCKED);
      }
    }
    return rows;
  }

  /** Ensures that Redis has a complete, ready projection before it is used for a write-side gate. */
  public boolean ensureReady(String sessionId) {
    try {
      return tryRebuildOrWait(sessionId).isPresent();
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  /** Rebuilds once or waits briefly; importantly, this method never queries MySQL as a waiter. */
  private java.util.Optional<InventoryLayoutProjection.ProjectionSnapshot> tryRebuildOrWait(String sessionId) {
    var ready = projection.readReady(sessionId);
    if (ready.isPresent()) return ready;
    boolean owner = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
        projection.rebuildLockKey(sessionId), sessionId, Duration.ofSeconds(15)));
    if (owner) {
      try {
        var afterAcquire = projection.readReady(sessionId);
        if (afterAcquire.isPresent()) return afterAcquire;
        var source = seats.listBySession(sessionId);
        var snapshot = projection.snapshot(sessionId, source, "v-" + UUID.randomUUID());
        projection.writeVersioned(sessionId, snapshot);
        return java.util.Optional.of(snapshot);
      } finally {
        redis.delete(projection.rebuildLockKey(sessionId));
      }
    }
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMillis);
    while (System.nanoTime() < deadline) {
      ready = projection.readReady(sessionId);
      if (ready.isPresent()) return ready;
      try {
        Thread.sleep(10L);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    return java.util.Optional.empty();
  }
}

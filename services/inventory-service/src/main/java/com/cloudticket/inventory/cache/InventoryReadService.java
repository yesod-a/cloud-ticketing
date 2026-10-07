package com.cloudticket.inventory.cache;

import com.cloudticket.inventory.persistence.InventorySeatRepository;
import com.cloudticket.inventory.persistence.entity.InventorySeatEntity;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Redis-first public inventory reads with a bounded cold-start rebuild. */
@Service
public class InventoryReadService {
  private final InventorySeatRepository seats;
  private final InventoryLayoutProjection projection;
  private final StringRedisTemplate redis;

  public InventoryReadService(InventorySeatRepository seats, InventoryLayoutProjection projection,
                              StringRedisTemplate redis) {
    this.seats = seats;
    this.projection = projection;
    this.redis = redis;
  }

  public List<InventorySeatEntity> seats(String sessionId) {
    try {
      var ready = projection.readReady(sessionId);
      if (ready.isPresent()) return projection.toEntities(sessionId, ready.get());
      boolean owner = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
          projection.rebuildLockKey(sessionId), sessionId, Duration.ofSeconds(15)));
      if (owner) {
        var source = seats.listBySession(sessionId);
        var snapshot = projection.snapshot(sessionId, source, "v-" + UUID.randomUUID());
        projection.writeVersioned(sessionId, snapshot);
        return projection.toEntities(sessionId, snapshot);
      }
      var afterWait = projection.readReady(sessionId);
      if (afterWait.isPresent()) return projection.toEntities(sessionId, afterWait.get());
    } catch (RuntimeException ignored) {
      // A read cache is optional; the durable inventory query remains the fallback.
    }
    return seats.listBySession(sessionId);
  }

  /** Ensures that Redis has a complete, ready projection before it is used for a write-side gate. */
  public boolean ensureReady(String sessionId) {
    try {
      if (projection.readReady(sessionId).isPresent()) return true;
      seats(sessionId);
      return projection.readReady(sessionId).isPresent();
    } catch (RuntimeException ignored) {
      return false;
    }
  }
}

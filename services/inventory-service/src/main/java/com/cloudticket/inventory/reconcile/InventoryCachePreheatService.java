package com.cloudticket.inventory.reconcile;

import com.cloudticket.inventory.cache.InventoryLayoutProjection;
import com.cloudticket.inventory.persistence.InventoryCacheWarmupRepository;
import com.cloudticket.inventory.persistence.InventorySeatRepository;
import com.cloudticket.inventory.persistence.entity.InventoryCacheWarmupEntity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Builds the Redis seat projection ahead of a sale without making Redis the source of truth. */
@Service
public class InventoryCachePreheatService {
  private final InventoryCacheWarmupRepository warmups;
  private final InventorySeatRepository seats;
  private final InventoryLayoutProjection projection;
  private final StringRedisTemplate redis;
  private final Executor executor;
  private final Clock clock;
  private final long lockSeconds;
  private final int batchSize;

  @Autowired
  public InventoryCachePreheatService(
      InventoryCacheWarmupRepository warmups,
      InventorySeatRepository seats,
      InventoryLayoutProjection projection,
      StringRedisTemplate redis,
      @Qualifier("inventoryCachePreheatExecutor") Executor executor,
      @Value("${cloudticket.inventory.preheat.lock-seconds:90}") long lockSeconds,
      @Value("${cloudticket.inventory.preheat.batch-size:100}") int batchSize) {
    this(warmups, seats, projection, redis, executor, Clock.systemUTC(), lockSeconds, batchSize);
  }

  public InventoryCachePreheatService(
      InventoryCacheWarmupRepository warmups,
      InventorySeatRepository seats,
      InventoryLayoutProjection projection,
      StringRedisTemplate redis,
      Executor executor,
      Clock clock,
      long lockSeconds,
      int batchSize) {
    this.warmups = warmups;
    this.seats = seats;
    this.projection = projection;
    this.redis = redis;
    this.executor = executor;
    this.clock = clock;
    this.lockSeconds = Math.max(15, lockSeconds);
    this.batchSize = Math.max(1, batchSize);
  }

  /** Claims due durable tasks and submits each session to the bounded worker pool. */
  public int preheatDue(String triggerId, int limit) {
    String workerId = (triggerId == null || triggerId.isBlank()) ? "inventory-preheat" : triggerId;
    Instant now = Instant.now(clock);
    warmups.releaseExpiredClaims(now);
    List<InventoryCacheWarmupEntity> claimed = warmups.claimDue(
        workerId, now, now.plusSeconds(lockSeconds), Math.min(Math.max(1, limit), batchSize));
    for (InventoryCacheWarmupEntity row : claimed) {
      executor.execute(() -> preheatSession(row.getSessionId(), workerId));
    }
    return claimed.size();
  }

  /** Rebuilds one session if this instance wins the Redis single-flight lock. */
  public void preheatSession(String sessionId, String workerId) {
    if (sessionId == null || sessionId.isBlank()) return;
    String owner = (workerId == null || workerId.isBlank()) ? "inventory-preheat" : workerId;
    String lockKey = projection.rebuildLockKey(sessionId);
    boolean acquired;
    try {
      acquired = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
          lockKey, owner, Duration.ofSeconds(lockSeconds)));
    } catch (Exception failure) {
      warmups.markFailed(sessionId, owner, failure.getMessage(),
          Instant.now(clock).plusSeconds(60));
      return;
    }
    if (!acquired) return;
    try {
      Instant now = Instant.now(clock);
      String version = "v-" + now.toEpochMilli();
      var snapshot = projection.snapshot(sessionId, seats.listBySession(sessionId), version);
      projection.writeVersioned(sessionId, snapshot);
      warmups.markReady(sessionId, owner, version, now);
    } catch (Exception failure) {
      Instant retryAt = Instant.now(clock).plusSeconds(Math.min(900, 30L * 2));
      warmups.markFailed(sessionId, owner, failure.getMessage(), retryAt);
    } finally {
      try {
        redis.delete(lockKey);
      } catch (Exception ignored) {
        // The TTL remains the final guard if Redis is unavailable during cleanup.
      }
    }
  }
}

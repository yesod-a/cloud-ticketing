package com.cloudticket.inventory.redis;

import com.cloudticket.inventory.cache.SeatBitmapProjection;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/** Redis admission protocol for queued seated reservations. MySQL remains the durable fact. */
@Component
public class QueuedSeatReservationService {
  private static final String PREFIX = "cloudticket:reservation:";
  private static final RedisScript<List> RESERVE = script("redis/reserve-queued-seats.lua");
  private static final RedisScript<Long> RELEASE = script("redis/release-queued-reservation.lua");
  private static final RedisScript<Long> CONFIRM = script("redis/confirm-queued-seats.lua");

  private final StringRedisTemplate redis;
  private final boolean enabled;
  private final long idempotencyTtlSeconds;

  public QueuedSeatReservationService(StringRedisTemplate redis,
      @Value("${cloudticket.queued.enabled:false}") boolean enabled,
      @Value("${cloudticket.queued.idempotency-ttl-seconds:86400}") long idempotencyTtlSeconds) {
    this.redis = redis;
    this.enabled = enabled && redis != null;
    this.idempotencyTtlSeconds = Math.max(60, idempotencyTtlSeconds);
  }

  public boolean isEnabled() { return enabled; }

  public AdmissionResult reserve(String reservationId, String userId, String sessionId,
                                 List<Integer> seatIndexes, long ttlSeconds, String idempotencyKey) {
    if (!enabled) throw new IllegalStateException("queued reservations are disabled");
    if (seatIndexes == null || seatIndexes.isEmpty()) throw new IllegalArgumentException("seats required");
    String tag = SeatBitmapProjection.tag(sessionId);
    List<String> keys = new ArrayList<>();
    keys.add(bitmapKey(tag, "sold"));
    keys.add(bitmapKey(tag, "locked"));
    keys.add(bitmapKey(tag, "disabled"));
    keys.add(pendingKey(tag));
    keys.add(reservationKey(tag, reservationId));
    keys.add(idempotencyKey(tag, idempotencyKey));
    seatIndexes.forEach(index -> keys.add(holdKey(tag, index)));
    long ttl = Math.max(1, Math.min(1800, ttlSeconds));
    long now = System.currentTimeMillis();
    List<String> args = new ArrayList<>(List.of(reservationId, reservationId, Long.toString(ttl * 1000),
        Long.toString(now), Long.toString(now + ttl * 1000),
        Long.toString(idempotencyTtlSeconds * 1000), sessionId, userId));
    seatIndexes.forEach(index -> args.add(String.valueOf(index)));
    List<?> result = redis.execute(RESERVE, keys, args.toArray());
    redis.opsForSet().add("cloudticket:queued:sessions", sessionId);
    long code = result == null || result.isEmpty() ? 0 : Long.parseLong(String.valueOf(result.get(0)));
    String existing = result != null && result.size() > 1 ? String.valueOf(result.get(1)) : reservationId;
    return new AdmissionResult(code == 1 || code == 2, existing, code == 2);
  }

  /** Requeues a leased reservation after a publisher crash or transient broker failure. */
  public void requeue(String reservationId, String sessionId, long nextAttemptAtMillis) {
    if (!enabled) return;
    String tag = SeatBitmapProjection.tag(sessionId);
    redis.opsForZSet().remove(inflightKey(tag), reservationId);
    redis.opsForZSet().add(pendingKey(tag), reservationId, nextAttemptAtMillis);
    redis.opsForHash().put(reservationKey(tag, reservationId), "status", "PENDING_PUBLISH");
  }

  public java.util.Set<String> due(String sessionId, long now, int limit) {
    if (!enabled) return java.util.Set.of();
    String tag = SeatBitmapProjection.tag(sessionId);
    return redis.opsForZSet().rangeByScore(pendingKey(tag), 0, now, 0, Math.max(1, limit));
  }

  public java.util.Set<String> inflightDue(String sessionId, long now, int limit) {
    if (!enabled) return java.util.Set.of();
    String tag = SeatBitmapProjection.tag(sessionId);
    return redis.opsForZSet().rangeByScore(inflightKey(tag), 0, now, 0, Math.max(1, limit));
  }

  public boolean claim(String reservationId, String sessionId, long leaseUntilMillis) {
    if (!enabled) return false;
    String tag = SeatBitmapProjection.tag(sessionId);
    Long removed = redis.opsForZSet().remove(pendingKey(tag), reservationId);
    if (!Long.valueOf(1L).equals(removed)) return false;
    redis.opsForZSet().add(inflightKey(tag), reservationId, leaseUntilMillis);
    redis.opsForHash().put(reservationKey(tag, reservationId), "status", "PUBLISHING");
    return true;
  }

  public void published(String reservationId, String sessionId) {
    if (!enabled) return;
    String tag = SeatBitmapProjection.tag(sessionId);
    redis.opsForZSet().remove(inflightKey(tag), reservationId);
    redis.opsForHash().put(reservationKey(tag, reservationId), "status", "PUBLISHED");
  }

  public java.util.Map<Object, Object> reservation(String reservationId, String sessionId) {
    if (!enabled) return java.util.Map.of();
    return redis.opsForHash().entries(reservationKey(SeatBitmapProjection.tag(sessionId), reservationId));
  }

  public java.util.Set<String> sessions() {
    if (!enabled) return java.util.Set.of();
    java.util.Set<String> sessions = redis.opsForSet().members("cloudticket:queued:sessions");
    return sessions == null ? java.util.Set.of() : sessions;
  }

  public boolean release(String reservationId, String sessionId, List<Integer> indexes) {
    if (!enabled) return true;
    String tag = SeatBitmapProjection.tag(sessionId);
    List<String> keys = new ArrayList<>(List.of(reservationKey(tag, reservationId), pendingKey(tag), inflightKey(tag),
        bitmapKey(tag, "locked")));
    indexes.forEach(index -> keys.add(holdKey(tag, index)));
    Long result = redis.execute(RELEASE, keys,
        reservationId, reservationId, "", Long.toString(System.currentTimeMillis()));
    return Long.valueOf(1L).equals(result);
  }

  public boolean confirm(String reservationId, String sessionId, List<Integer> indexes) {
    if (!enabled) return true;
    String tag = SeatBitmapProjection.tag(sessionId);
    List<String> keys = new ArrayList<>(List.of(reservationKey(tag, reservationId),
        bitmapKey(tag, "sold"), bitmapKey(tag, "locked"), pendingKey(tag), inflightKey(tag)));
    indexes.forEach(index -> keys.add(holdKey(tag, index)));
    Long result = redis.execute(CONFIRM, keys, reservationId, Long.toString(System.currentTimeMillis()));
    return Long.valueOf(1L).equals(result);
  }

  public String pendingKey(String tag) { return PREFIX + "{" + tag + "}:pending"; }
  public String inflightKey(String tag) { return PREFIX + "{" + tag + "}:inflight"; }
  public String idempotencyKey(String tag, String key) { return PREFIX + "{" + tag + "}:idempotency:" + key; }
  public String reservationKey(String tag, String id) { return PREFIX + "{" + tag + "}:reservation:" + id; }
  public String holdKey(String tag, int index) { return PREFIX + "{" + tag + "}:hold:" + index; }
  private static String bitmapKey(String tag, String name) { return "cloudticket:inventory:" + tag + ":" + name; }
  public record AdmissionResult(boolean accepted, String reservationId, boolean duplicate) {}

  @SuppressWarnings("unchecked")
  private static <T> RedisScript<T> script(String path) {
    DefaultRedisScript<T> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource(path));
    return script;
  }
}

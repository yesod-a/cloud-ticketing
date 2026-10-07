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

/** Atomic Redis quantity reservation for GENERAL_ADMISSION sessions. */
@Component
public class QueuedAdmissionReservationService {
  private static final String PREFIX = "cloudticket:reservation:";
  private static final RedisScript<List> RESERVE = script("redis/reserve-queued-admission.lua");
  private static final RedisScript<Long> RELEASE = script("redis/release-queued-admission.lua");
  private static final RedisScript<Long> CONFIRM = script("redis/confirm-queued-admission.lua");
  private final StringRedisTemplate redis;
  private final boolean enabled;
  private final long idempotencyTtlSeconds;

  public QueuedAdmissionReservationService(StringRedisTemplate redis,
      @Value("${cloudticket.queued.enabled:false}") boolean enabled,
      @Value("${cloudticket.queued.idempotency-ttl-seconds:86400}") long idempotencyTtlSeconds) {
    this.redis = redis; this.enabled = enabled && redis != null;
    this.idempotencyTtlSeconds = Math.max(60, idempotencyTtlSeconds);
  }

  public boolean isEnabled() { return enabled; }

  public void initialize(String sessionId, int capacity, long nextTicketNumber) {
    initialize(sessionId, capacity, nextTicketNumber, capacity, 0, 0);
  }

  public void initialize(String sessionId, int capacity, long nextTicketNumber,
                         int available, int held, int sold) {
    if (!enabled) throw new IllegalStateException("queued reservations are disabled");
    String tag = tag(sessionId);
    redis.opsForValue().setIfAbsent(availableKey(tag), Integer.toString(Math.max(0, available)));
    redis.opsForValue().setIfAbsent(heldKey(tag), Integer.toString(Math.max(0, held)));
    redis.opsForValue().setIfAbsent(soldKey(tag), Integer.toString(Math.max(0, sold)));
    redis.opsForValue().setIfAbsent(nextKey(tag), Long.toString(Math.max(1, nextTicketNumber) - 1));
  }

  public Result reserve(String reservationId, String userId, String sessionId, int quantity,
                        long ttlSeconds, String idempotencyKey) {
    if (!enabled) throw new IllegalStateException("queued reservations are disabled");
    if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
    String tag = tag(sessionId);
    List<String> keys = List.of(availableKey(tag), heldKey(tag), nextKey(tag), pendingKey(tag),
        reservationKey(tag, reservationId), idempotencyKey(tag, idempotencyKey));
    long ttl = Math.max(1, Math.min(1800, ttlSeconds)) * 1000;
    long now = System.currentTimeMillis();
    List<String> args = List.of(reservationId, Long.toString(ttl), Long.toString(now),
        Long.toString(now + ttl), Long.toString(idempotencyTtlSeconds * 1000), sessionId, userId,
        Integer.toString(quantity));
    List<?> result = redis.execute(RESERVE, keys, args.toArray());
    redis.opsForSet().add("cloudticket:queued:sessions", sessionId);
    long code = result == null || result.isEmpty() ? 0 : Long.parseLong(String.valueOf(result.get(0)));
    String existing = result != null && result.size() > 1 ? String.valueOf(result.get(1)) : reservationId;
    long first = result != null && result.size() > 2 ? Long.parseLong(String.valueOf(result.get(2))) : 0;
    return new Result(code == 1 || code == 2, existing, first, code == 2);
  }

  public boolean release(String reservationId, String sessionId, int quantity) {
    if (!enabled) return true;
    String tag = tag(sessionId);
    Long result = redis.execute(RELEASE, List.of(availableKey(tag), heldKey(tag), reservationKey(tag, reservationId),
        pendingKey(tag), inflightKey(tag)), reservationId, Integer.toString(quantity), Long.toString(System.currentTimeMillis()));
    return Long.valueOf(1L).equals(result);
  }

  public boolean confirm(String reservationId, String sessionId, int quantity) {
    if (!enabled) return true;
    String tag = tag(sessionId);
    Long result = redis.execute(CONFIRM, List.of(reservationKey(tag, reservationId), heldKey(tag), soldKey(tag), pendingKey(tag), inflightKey(tag)),
        reservationId, Integer.toString(quantity), Long.toString(System.currentTimeMillis()));
    return Long.valueOf(1L).equals(result);
  }

  public java.util.Map<Object, Object> reservation(String reservationId, String sessionId) {
    if (!enabled) return java.util.Map.of();
    return redis.opsForHash().entries(reservationKey(tag(sessionId), reservationId));
  }

  public java.util.Set<String> sessions() {
    if (!enabled) return java.util.Set.of();
    java.util.Set<String> values = redis.opsForSet().members("cloudticket:queued:sessions");
    return values == null ? java.util.Set.of() : values;
  }

  private static String tag(String sessionId) { return SeatBitmapProjection.tag(sessionId); }
  private static String pendingKey(String tag) { return PREFIX + "{" + tag + "}:pending"; }
  private static String inflightKey(String tag) { return PREFIX + "{" + tag + "}:inflight"; }
  private static String availableKey(String tag) { return PREFIX + "{" + tag + "}:ga:available"; }
  private static String heldKey(String tag) { return PREFIX + "{" + tag + "}:ga:held"; }
  private static String soldKey(String tag) { return PREFIX + "{" + tag + "}:ga:sold"; }
  private static String nextKey(String tag) { return PREFIX + "{" + tag + "}:ga:next-ticket"; }
  private static String reservationKey(String tag, String id) { return PREFIX + "{" + tag + "}:reservation:" + id; }
  private static String idempotencyKey(String tag, String key) { return PREFIX + "{" + tag + "}:idempotency:" + key; }
  public record Result(boolean accepted, String reservationId, long firstTicketNumber, boolean duplicate) {}
  @SuppressWarnings("unchecked") private static <T> RedisScript<T> script(String path) {
    DefaultRedisScript<T> script = new DefaultRedisScript<>(); script.setLocation(new ClassPathResource(path)); return script;
  }
}

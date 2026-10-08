package com.cloudticket.inventory.redis;

import com.cloudticket.inventory.cache.SeatBitmapProjection;
import com.cloudticket.inventory.cache.InventoryReadService;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/** Redis-backed temporary hold protocol. MySQL remains the durable inventory decision. */
@Component
public class RedisSeatLockService {

  private static final String PREFIX = "cloudticket:inventory:";
  private static final RedisScript<Long> LOCK_SCRIPT = script("redis/lock-seats.lua");
  private static final RedisScript<Long> RELEASE_SCRIPT = script("redis/release-seats.lua");
  private static final RedisScript<Long> PROMOTE_SCRIPT = script("redis/promote-seats.lua");

  private final StringRedisTemplate redis;
  private final boolean enabled;
  private final InventoryReadService reads;

  public RedisSeatLockService(StringRedisTemplate redis,
                              @Value("${cloudticket.inventory.redis-lock.enabled:${cloudticket.redis-seat-lock.enabled:false}}") boolean enabled) {
    this(redis, enabled, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public RedisSeatLockService(StringRedisTemplate redis,
                              @Value("${cloudticket.inventory.redis-lock.enabled:${cloudticket.redis-seat-lock.enabled:true}}") boolean enabled,
                              InventoryReadService reads) {
    this.redis = redis;
    this.enabled = enabled;
    this.reads = reads;
  }

  public boolean isEnabled() { return enabled && redis != null; }

  /** Redis seat gates are only safe when the complete projection is ready. */
  public boolean isReady(String sessionId) {
    return !isEnabled() || reads == null || reads.ensureReady(sessionId);
  }

  /** Returns true when all requested holds were created, or when this optional path is disabled. */
  public boolean reserve(String orderId, String sessionId, List<String> seatIds, long ttlSeconds) {
    if (!isEnabled()) return true;
    List<Integer> indexes = java.util.stream.IntStream.range(0, seatIds.size()).boxed().toList();
    return reserveIndexed(orderId, sessionId, indexes, ttlSeconds);
  }

  public boolean reserveIndexed(String orderId, String sessionId, List<Integer> seatIndexes, long ttlSeconds) {
    return prepareIndexed(orderId, sessionId, seatIndexes, ttlSeconds);
  }

  /** Acquires the short-lived Redis part of the two-phase reservation protocol. */
  public boolean prepareIndexed(String orderId, String sessionId, List<Integer> seatIndexes, long ignoredTtlSeconds) {
    if (!isEnabled()) return true;
    if (!isReady(sessionId)) return false;
    List<String> keys = gateKeys(sessionId, seatIndexes);
    List<String> args = new java.util.ArrayList<>();
    args.add(token(orderId, sessionId, "PREPARED"));
    args.add("30000");
    args.addAll(seatIndexes.stream().map(String::valueOf).toList());
    Long result = redis.execute(LOCK_SCRIPT, keys, args.toArray(String[]::new));
    return Long.valueOf(1L).equals(result);
  }

  /** Extends prepared holds only after the MySQL order row exists. */
  public boolean promoteIndexed(String orderId, String sessionId, List<Integer> seatIndexes, long ttlSeconds) {
    if (!isEnabled()) return true;
    long ttlMillis = Math.max(1_000L, Math.min(ttlSeconds, 1_800L) * 1_000L);
    List<String> keys = seatIndexes.stream().map(index -> key(sessionId, Integer.toString(index))).toList();
    Long result = redis.execute(PROMOTE_SCRIPT, keys, token(orderId, sessionId), Long.toString(ttlMillis));
    return Long.valueOf(1L).equals(result);
  }

  /** Returns true when all matching holds were released, or when this optional path is disabled. */
  public boolean release(String orderId, String sessionId, List<String> seatIds) {
    if (!isEnabled()) return true;
    List<String> keys = seatIds.stream().map(id -> key(sessionId, id)).toList();
    Long result = redis.execute(RELEASE_SCRIPT, keys, token(orderId, sessionId));
    return Long.valueOf(1L).equals(result);
  }

  public boolean releaseIndexed(String orderId, String sessionId, List<Integer> seatIndexes) {
    if (!isEnabled()) return true;
    List<String> keys = seatIndexes.stream().map(index -> key(sessionId, Integer.toString(index))).toList();
    Long result = redis.execute(RELEASE_SCRIPT, keys, token(orderId, sessionId));
    return Long.valueOf(1L).equals(result);
  }

  public Map<Integer, String> holds(String sessionId) {
    if (!isEnabled()) return Map.of();
    String prefix = PREFIX + SeatBitmapProjection.tag(sessionId) + ":hold:";
    return redis.execute((RedisCallback<Map<Integer, String>>) connection -> {
      Map<Integer, String> holds = new LinkedHashMap<>();
      try (var cursor = connection.scan(org.springframework.data.redis.core.ScanOptions.scanOptions()
          .match(prefix + "*").count(500).build())) {
        while (cursor.hasNext()) {
          byte[] rawKey = cursor.next();
          String raw = new String(rawKey, StandardCharsets.UTF_8);
          String suffix = raw.substring(prefix.length());
          try {
            int index = Integer.parseInt(suffix);
            byte[] value = connection.get(rawKey);
            if (value != null) holds.put(index, new String(value, StandardCharsets.UTF_8));
          } catch (NumberFormatException ignored) {
            // Ignore keys that do not follow the stable seat-index format.
          }
        }
      }
      return holds;
    });
  }

  public boolean restoreHoldIfAbsent(String orderId, String sessionId, int seatIndex, long ttlSeconds) {
    if (!isEnabled()) return false;
    return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(sessionId, Integer.toString(seatIndex)),
        token(orderId, sessionId), Duration.ofSeconds(Math.max(1, Math.min(ttlSeconds, 1_800)))));
  }

  public boolean releaseHoldIfToken(String sessionId, int seatIndex, String expectedToken) {
    if (!isEnabled()) return false;
    Long result = redis.execute(RELEASE_SCRIPT,
        List.of(key(sessionId, Integer.toString(seatIndex))), expectedToken);
    return Long.valueOf(1L).equals(result);
  }

  public static String key(String sessionId, String seatId) {
    if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("sessionId required");
    if (seatId == null || seatId.isBlank()) throw new IllegalArgumentException("seatId required");
    return PREFIX + "{" + sessionId.trim() + "}:hold:" + seatId.trim();
  }

  public static String token(String orderId, String sessionId) {
    return orderId + ":" + sessionId;
  }

  public static String token(String orderId, String sessionId, String phase) {
    return token(orderId, sessionId) + ":" + phase;
  }

  private List<String> gateKeys(String sessionId, List<Integer> seatIndexes) {
    String tag = SeatBitmapProjection.tag(sessionId);
    List<String> keys = new java.util.ArrayList<>();
    keys.add(readyKey(tag));
    keys.add(bitmapKey(tag, "sold"));
    keys.add(bitmapKey(tag, "locked"));
    keys.add(bitmapKey(tag, "disabled"));
    keys.addAll(seatIndexes.stream().map(index -> key(sessionId, Integer.toString(index))).toList());
    return keys;
  }

  private static String bitmapKey(String tag, String name) { return PREFIX + tag + ":" + name; }
  private static String readyKey(String tag) { return PREFIX + tag + ":ready"; }

  private static RedisScript<Long> script(String path) {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource(path));
    script.setResultType(Long.class);
    return script;
  }
}

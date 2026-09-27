package com.cloudticket.inventory.cache;

import com.cloudticket.inventory.persistence.InventorySeatRepository;
import com.cloudticket.inventory.persistence.entity.InventorySeatEntity;
import java.util.List;
import java.nio.charset.StandardCharsets;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

/**
 * Read-only Redis projection for one session's seat statuses.
 *
 * <p>MySQL remains the source of truth. A missing or unavailable projection is a cache miss and
 * returns the database rows unchanged; callers never infer availability from Redis alone.
 */
@Component
public class SeatBitmapProjection {

  private static final String PREFIX = "cloudticket:inventory:";
  private static final String READY = "ready";

  private final StringRedisTemplate redis;
  private final boolean enabled;

  @org.springframework.beans.factory.annotation.Autowired
  public SeatBitmapProjection(
      StringRedisTemplate redis,
      @org.springframework.beans.factory.annotation.Value("${cloudticket.seat-bitmap.enabled:true}") boolean enabled) {
    this.redis = redis;
    this.enabled = enabled;
  }

  public List<InventorySeatEntity> overlay(String sessionId, List<InventorySeatEntity> source) {
    if (!enabled || redis == null || source == null || source.isEmpty()) return source;
    try {
      ValueOperations<String, String> values = redis.opsForValue();
      String tag = tag(sessionId);
      if (!READY.equals(values.get(versionKey(tag)))) return source;
      byte[] sold = bitmap(soldKey(tag));
      byte[] locked = bitmap(lockedKey(tag));
      byte[] disabled = bitmap(disabledKey(tag));
      for (int index = 0; index < source.size(); index++) {
        int seatIndex = source.get(index).getSeatIndex() == null ? index : source.get(index).getSeatIndex();
        String status = status(sold, locked, disabled, seatIndex);
        if (status != null) source.get(index).setStatus(status);
      }
      return source;
    } catch (RuntimeException unavailable) {
      return source;
    }
  }

  /** Rebuilds all status bitmaps from durable inventory rows. */
  public void rebuild(String sessionId, List<InventorySeatEntity> source) {
    if (!enabled || redis == null || source == null) return;
    String tag = tag(sessionId);
    try {
      redis.delete(List.of(soldKey(tag), lockedKey(tag), disabledKey(tag), versionKey(tag)));
      int bitCount = source.stream().map(InventorySeatEntity::getSeatIndex).filter(java.util.Objects::nonNull)
          .mapToInt(Integer::intValue).max().orElse(source.size() - 1) + 1;
      byte[][] bitmaps = {new byte[(bitCount + 7) / 8], new byte[(bitCount + 7) / 8],
          new byte[(bitCount + 7) / 8]};
      for (int index = 0; index < source.size(); index++) {
        String status = source.get(index).getStatus();
        int seatIndex = source.get(index).getSeatIndex() == null ? index : source.get(index).getSeatIndex();
        if (InventorySeatRepository.SOLD.equals(status)) setBit(bitmaps[0], seatIndex);
        if (InventorySeatRepository.LOCKED.equals(status)) setBit(bitmaps[1], seatIndex);
        if (InventorySeatRepository.DISABLED.equals(status)) setBit(bitmaps[2], seatIndex);
      }
      setBitmap(soldKey(tag), bitmaps[0]);
      setBitmap(lockedKey(tag), bitmaps[1]);
      setBitmap(disabledKey(tag), bitmaps[2]);
      ValueOperations<String, String> values = redis.opsForValue();
      values.set(versionKey(tag), READY);
    } catch (RuntimeException ignored) {
      // Projection writes are best effort. The database remains authoritative.
    }
  }

  public void invalidate(String sessionId) {
    if (!enabled || redis == null) return;
    String tag = tag(sessionId);
    try {
      redis.delete(List.of(soldKey(tag), lockedKey(tag), disabledKey(tag), versionKey(tag)));
    } catch (RuntimeException ignored) {
      // A later read falls back to MySQL and can rebuild the projection.
    }
  }

  public boolean isReady(String sessionId) {
    if (!enabled || redis == null) return false;
    try {
      return READY.equals(redis.opsForValue().get(versionKey(tag(sessionId))));
    } catch (RuntimeException unavailable) {
      return false;
    }
  }

  public static String tag(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("sessionId required");
    return "{" + sessionId.trim() + "}";
  }

  private static String status(byte[] sold, byte[] locked, byte[] disabled, int index) {
    if (getBit(sold, index)) return InventorySeatRepository.SOLD;
    if (getBit(locked, index)) return InventorySeatRepository.LOCKED;
    if (getBit(disabled, index)) return InventorySeatRepository.DISABLED;
    return InventorySeatRepository.AVAILABLE;
  }

  private byte[] bitmap(String key) {
    return redis.execute((RedisCallback<byte[]>) connection -> connection.get(raw(key)));
  }

  private void setBitmap(String key, byte[] value) {
    if (value.length == 0) return;
    redis.execute((RedisCallback<Object>) connection -> {
      connection.set(raw(key), value);
      return null;
    });
  }

  private static byte[] raw(String key) { return key.getBytes(StandardCharsets.UTF_8); }

  private static void setBit(byte[] bytes, int index) {
    bytes[index / 8] |= (byte) (1 << (7 - index % 8));
  }

  private static boolean getBit(byte[] bytes, int index) {
    return bytes != null && index / 8 < bytes.length
        && (bytes[index / 8] & (1 << (7 - index % 8))) != 0;
  }

  private static String soldKey(String tag) { return PREFIX + tag + ":sold"; }
  private static String lockedKey(String tag) { return PREFIX + tag + ":locked"; }
  private static String disabledKey(String tag) { return PREFIX + tag + ":disabled"; }
  private static String versionKey(String tag) { return PREFIX + tag + ":version"; }
}

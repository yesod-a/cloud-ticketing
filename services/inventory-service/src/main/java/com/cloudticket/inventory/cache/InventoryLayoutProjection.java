package com.cloudticket.inventory.cache;

import com.cloudticket.inventory.persistence.InventorySeatRepository;
import com.cloudticket.inventory.persistence.entity.InventorySeatEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Versioned Redis read projection for a complete session seat map. */
@Component
public class InventoryLayoutProjection {
  private static final String PREFIX = "cloudticket:inventory:";
  private final StringRedisTemplate redis;
  private final ObjectMapper json;

  public InventoryLayoutProjection(StringRedisTemplate redis, ObjectMapper json) {
    this.redis = redis;
    this.json = json;
  }

  public ProjectionSnapshot snapshot(String sessionId, List<InventorySeatEntity> source, String version) {
    if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("sessionId required");
    if (version == null || version.isBlank()) throw new IllegalArgumentException("version required");
    int maxIndex = source == null ? -1 : source.stream().map(InventorySeatEntity::getSeatIndex)
        .filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).max().orElse(-1);
    int bitCount = Math.max(source == null ? 0 : source.size(), maxIndex + 1);
    byte[] sold = new byte[(bitCount + 7) / 8];
    byte[] locked = new byte[(bitCount + 7) / 8];
    byte[] disabled = new byte[(bitCount + 7) / 8];
    List<LayoutSeat> layout = new java.util.ArrayList<>();
    if (source != null) {
      for (int offset = 0; offset < source.size(); offset++) {
        InventorySeatEntity seat = source.get(offset);
        int index = seat.getSeatIndex() == null ? offset : seat.getSeatIndex();
        layout.add(LayoutSeat.from(seat, index));
        if (InventorySeatRepository.SOLD.equals(seat.getStatus())) setBit(sold, index);
        if (InventorySeatRepository.LOCKED.equals(seat.getStatus())) setBit(locked, index);
        if (InventorySeatRepository.DISABLED.equals(seat.getStatus())) setBit(disabled, index);
      }
    }
    try {
      return new ProjectionSnapshot(version, json.writeValueAsString(layout), sold, locked, disabled);
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException("unable to serialize inventory layout", failure);
    }
  }

  public void writeVersioned(String sessionId, ProjectionSnapshot snapshot) {
    String tag = SeatBitmapProjection.tag(sessionId);
    String version = snapshot.version();
    redis.opsForValue().set(readyKey(tag), "0");
    redis.opsForValue().set(layoutKey(tag, version), snapshot.layoutJson());
    redis.execute((RedisCallback<Object>) connection -> {
      connection.set(raw(soldKey(tag, version)), snapshot.soldBitmap());
      connection.set(raw(lockedKey(tag, version)), snapshot.lockedBitmap());
      connection.set(raw(disabledKey(tag, version)), snapshot.disabledBitmap());
      // Legacy keys are retained for the existing Lua lock scripts.
      connection.set(raw(legacyKey(tag, "sold")), snapshot.soldBitmap());
      connection.set(raw(legacyKey(tag, "locked")), snapshot.lockedBitmap());
      connection.set(raw(legacyKey(tag, "disabled")), snapshot.disabledBitmap());
      return null;
    });
    redis.opsForValue().set(currentVersionKey(tag), version);
    redis.opsForValue().set(readyKey(tag), version);
  }

  public Optional<ProjectionSnapshot> readReady(String sessionId) {
    if (redis == null) return Optional.empty();
    String tag = SeatBitmapProjection.tag(sessionId);
    String ready = redis.opsForValue().get(readyKey(tag));
    String version = redis.opsForValue().get(currentVersionKey(tag));
    if (ready == null || "0".equals(ready) || version == null || !version.equals(ready)) return Optional.empty();
    String layout = redis.opsForValue().get(layoutKey(tag, version));
    byte[] sold = rawGet(soldKey(tag, version));
    byte[] locked = rawGet(lockedKey(tag, version));
    byte[] disabled = rawGet(disabledKey(tag, version));
    if (layout == null || sold == null || locked == null || disabled == null) return Optional.empty();
    return Optional.of(new ProjectionSnapshot(version, layout, sold, locked, disabled));
  }

  public List<InventorySeatEntity> toEntities(String sessionId, ProjectionSnapshot snapshot) {
    try {
      LayoutSeat[] layout = json.readValue(snapshot.layoutJson(), LayoutSeat[].class);
      List<InventorySeatEntity> rows = new java.util.ArrayList<>();
      for (LayoutSeat item : layout) {
        InventorySeatEntity row = item.entity(sessionId, status(snapshot, item.seatIndex()));
        rows.add(row);
      }
      return rows;
    } catch (Exception failure) {
      throw new IllegalStateException("unable to read inventory layout", failure);
    }
  }

  public void invalidate(String sessionId) {
    String tag = SeatBitmapProjection.tag(sessionId);
    redis.opsForValue().set(readyKey(tag), "0");
  }

  public String rebuildLockKey(String sessionId) {
    return PREFIX + SeatBitmapProjection.tag(sessionId) + ":rebuild-lock";
  }

  private String status(ProjectionSnapshot snapshot, int index) {
    if (getBit(snapshot.soldBitmap(), index)) return InventorySeatRepository.SOLD;
    if (getBit(snapshot.lockedBitmap(), index)) return InventorySeatRepository.LOCKED;
    if (getBit(snapshot.disabledBitmap(), index)) return InventorySeatRepository.DISABLED;
    return InventorySeatRepository.AVAILABLE;
  }

  private byte[] rawGet(String key) {
    return redis.execute((RedisCallback<byte[]>) connection -> connection.get(raw(key)));
  }

  private static String layoutKey(String tag, String version) { return PREFIX + tag + ":layout:" + version; }
  private static String soldKey(String tag, String version) { return PREFIX + tag + ":sold:" + version; }
  private static String lockedKey(String tag, String version) { return PREFIX + tag + ":locked:" + version; }
  private static String disabledKey(String tag, String version) { return PREFIX + tag + ":disabled:" + version; }
  private static String legacyKey(String tag, String name) { return PREFIX + tag + ":" + name; }
  private static String currentVersionKey(String tag) { return PREFIX + tag + ":current-version"; }
  private static String readyKey(String tag) { return PREFIX + tag + ":ready"; }
  private static byte[] raw(String key) { return key.getBytes(StandardCharsets.UTF_8); }

  private static void setBit(byte[] bytes, int index) { bytes[index / 8] |= (byte) (1 << (7 - index % 8)); }
  private static boolean getBit(byte[] bytes, int index) {
    return bytes != null && index >= 0 && index / 8 < bytes.length
        && (bytes[index / 8] & (1 << (7 - index % 8))) != 0;
  }

  public record ProjectionSnapshot(String version, String layoutJson, byte[] soldBitmap,
                                   byte[] lockedBitmap, byte[] disabledBitmap) {}

  private record LayoutSeat(String id, String activityId, String areaLabel, String rowLabel, Integer seatNumber,
                            String displayName, String seatType, Integer seatIndex, BigDecimal positionX,
                            BigDecimal positionY) {
    static LayoutSeat from(InventorySeatEntity seat, int index) {
      return new LayoutSeat(seat.getId(), seat.getActivityId(), seat.getAreaLabel(), seat.getRowLabel(),
          seat.getSeatNumber(), seat.getDisplayName(), seat.getSeatType(), index, seat.getPositionX(), seat.getPositionY());
    }
    InventorySeatEntity entity(String sessionId, String status) {
      return InventorySeatRepository.seat(id, sessionId, activityId, areaLabel, rowLabel,
          seatNumber == null ? 0 : seatNumber, displayName, seatType, positionX, positionY, status);
    }
  }
}

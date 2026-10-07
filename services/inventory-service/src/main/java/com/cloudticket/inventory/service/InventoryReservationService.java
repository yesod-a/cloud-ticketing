package com.cloudticket.inventory.service;

import com.cloudticket.common.domain.SeatIds;
import com.cloudticket.inventory.persistence.InventoryLockRepository;
import com.cloudticket.inventory.persistence.InventorySeatRepository;
import com.cloudticket.inventory.cache.SeatBitmapProjection;
import com.cloudticket.inventory.cache.InventoryLayoutProjection;
import com.cloudticket.inventory.redis.RedisSeatLockService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Holds and releases seats for an order.
 *
 * <p>A lock is one conditional UPDATE plus one insert per seat: the database decides which of two
 * concurrent requests wins, and {@code inventory_lock} keeps the order's held seats durable across
 * restarts.
 */
@Service
public class InventoryReservationService {

  private static final long MAX_TTL_SECONDS = 1800;

  private final InventorySeatRepository seats;
  private final InventoryLockRepository locks;
  private final SeatBitmapProjection projection;
  private final RedisSeatLockService redisLocks;
  private final InventoryLayoutProjection layoutProjection;

  public InventoryReservationService(InventorySeatRepository seats, InventoryLockRepository locks) {
    this(seats, locks, null, null, null);
  }

  public InventoryReservationService(InventorySeatRepository seats, InventoryLockRepository locks,
                                     SeatBitmapProjection projection) {
    this(seats, locks, projection, null, null);
  }

  public InventoryReservationService(InventorySeatRepository seats, InventoryLockRepository locks,
                                     SeatBitmapProjection projection, RedisSeatLockService redisLocks) {
    this(seats, locks, projection, redisLocks, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public InventoryReservationService(InventorySeatRepository seats, InventoryLockRepository locks,
                                     SeatBitmapProjection projection, RedisSeatLockService redisLocks,
                                     InventoryLayoutProjection layoutProjection) {
    this.seats = seats;
    this.locks = locks;
    this.projection = projection;
    this.redisLocks = redisLocks;
    this.layoutProjection = layoutProjection;
  }

  public static List<String> normalizeSeatIds(List<String> seatIds) {
    return SeatIds.normalize(seatIds);
  }

  @Transactional
  public Reservation reserve(String orderId, String sessionId, List<String> requestedSeatIds, long ttlSeconds) {
    if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("orderId required");
    if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("sessionId required");
    List<String> seatIds = SeatIds.normalize(requestedSeatIds);

    List<String> alreadyHeld = locks.activeSeatIds(orderId);
    if (!alreadyHeld.isEmpty()) {
      // Retrying the same order must not take a second hold on the same seats.
      return new Reservation(orderId, sessionId, alreadyHeld,
          Instant.now().plusSeconds(Math.max(1, ttlSeconds)));
    }
    boolean redisHeld = false;
    Map<String, Integer> seatIndexes = Map.of();
    if (redisLocks != null && redisLocks.isEnabled() && redisLocks.isReady(sessionId)) {
      seatIndexes = seats.indexes(sessionId, seatIds);
      if (seatIndexes.size() != seatIds.size()) throw new SeatsUnavailableException();
      redisHeld = redisLocks.reserveIndexed(orderId, sessionId, seatIds.stream().map(seatIndexes::get).toList(), ttlSeconds);
      if (!redisHeld) throw new SeatsUnavailableException();
    }
    try {
      if (seats.lock(sessionId, seatIds) != seatIds.size()) throw new SeatsUnavailableException();

      long ttl = Math.max(1, Math.min(ttlSeconds, MAX_TTL_SECONDS));
      Instant expiresAt = Instant.now().plusSeconds(ttl);
      seatIds.forEach(seatId -> locks.hold(orderId, sessionId, seatId, expiresAt));
      invalidate(List.of(sessionId));
      return new Reservation(orderId, sessionId, seatIds, expiresAt);
    } catch (RuntimeException failure) {
      if (redisHeld) {
        List<Integer> indexes = seatIds.stream().map(seatIndexes::get).filter(java.util.Objects::nonNull).toList();
        redisLocks.releaseIndexed(orderId, sessionId, indexes);
      }
      throw failure;
    }
  }

  @Transactional
  public void release(String orderId) {
    if (orderId == null || orderId.isBlank()) return;
    List<String> sessions = locks.activeSessionIds(orderId);
    List<String> held = locks.activeSeatIds(orderId);
    locks.retireActive(orderId, InventoryLockRepository.RELEASED);
    seats.release(held);
    invalidate(sessions);
    releaseRedis(orderId, sessions, held);
  }

  /** Converts a paid order's active locks into permanently sold seats. */
  @Transactional
  public int confirm(String orderId) {
    if (orderId == null || orderId.isBlank()) return 0;
    List<String> sessions = locks.activeSessionIds(orderId);
    List<String> held = locks.activeSeatIds(orderId);
    if (held.isEmpty()) return 0;
    int sold = seats.sell(held);
    locks.retireActive(orderId, InventoryLockRepository.CONFIRMED);
    invalidate(sessions);
    releaseRedis(orderId, sessions, held);
    return sold;
  }

  private void releaseRedis(String orderId, List<String> sessions, List<String> held) {
    if (redisLocks == null || !redisLocks.isEnabled() || held.isEmpty() || sessions.size() != 1) return;
    Map<String, Integer> indexes = seats.indexes(sessions.get(0), held);
    if (indexes.size() == held.size()) {
      redisLocks.releaseIndexed(orderId, sessions.get(0), held.stream().map(indexes::get).toList());
    } else {
      redisLocks.release(orderId, sessions.get(0), held);
    }
  }

  private void invalidate(List<String> sessions) {
    if (projection != null) sessions.forEach(projection::invalidate);
    if (layoutProjection != null) sessions.forEach(layoutProjection::invalidate);
  }

  @Scheduled(fixedDelayString = "${cloudticket.inventory-expiry-scan-ms:30000}")
  @Transactional
  public void expireReservations() {
    locks.expiredOrderIds().forEach(this::release);
  }

  public record Reservation(String orderId, String sessionId, List<String> seatIds, Instant expiresAt) {}

  @ResponseStatus(HttpStatus.CONFLICT)
  public static final class SeatsUnavailableException extends RuntimeException {
    public SeatsUnavailableException() {
      super("one or more seats are unavailable");
    }
  }
}

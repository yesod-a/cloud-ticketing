package com.cloudticket.inventory.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cloudticket.inventory.persistence.entity.InventoryLockEntity;
import com.cloudticket.inventory.persistence.mapper.InventoryLockMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Persistence for {@code inventory_lock}. */
@Repository
public class InventoryLockRepository {

  public static final int ACTIVE = 1;
  public static final String PREPARED = "PREPARED";
  public static final String ACTIVE_STATUS = "ACTIVE";
  public static final String RELEASED = "RELEASED";
  public static final String CONFIRMED = "CONFIRMED";

  private final InventoryLockMapper locks;

  public InventoryLockRepository(InventoryLockMapper locks) {
    this.locks = locks;
  }

  public List<String> activeSeatIds(String orderId) {
    return locks.selectList(Wrappers.<InventoryLockEntity>lambdaQuery()
            .eq(InventoryLockEntity::getOrderId, orderId)
            .eq(InventoryLockEntity::getActive, ACTIVE))
        .stream()
        .map(InventoryLockEntity::getSeatId)
        .toList();
  }

  public List<String> activeSessionIds(String orderId) {
    return locks.selectList(Wrappers.<InventoryLockEntity>lambdaQuery()
            .select(InventoryLockEntity::getSessionId)
            .eq(InventoryLockEntity::getOrderId, orderId)
            .eq(InventoryLockEntity::getActive, ACTIVE))
        .stream()
        .map(InventoryLockEntity::getSessionId)
        .filter(java.util.Objects::nonNull)
        .distinct()
        .toList();
  }

  public void hold(String orderId, String sessionId, String seatId, Instant expiresAt) {
    hold(orderId, sessionId, seatId, expiresAt, ACTIVE_STATUS);
  }

  public void holdPrepared(String orderId, String sessionId, String seatId, Instant expiresAt) {
    hold(orderId, sessionId, seatId, expiresAt, PREPARED);
  }

  private void hold(String orderId, String sessionId, String seatId, Instant expiresAt, String status) {
    InventoryLockEntity lock = new InventoryLockEntity();
    lock.setId(UUID.randomUUID().toString());
    lock.setOrderId(orderId);
    lock.setSessionId(sessionId);
    lock.setSeatId(seatId);
    lock.setActive(ACTIVE);
    lock.setStatus(status);
    lock.setExpiresAt(expiresAt);
    locks.insert(lock);
  }

  public void holdBatch(String orderId, String sessionId, List<String> seatIds, Instant expiresAt) {
    holdBatch(orderId, sessionId, seatIds, expiresAt, ACTIVE_STATUS);
  }

  public void holdPreparedBatch(String orderId, String sessionId, List<String> seatIds, Instant expiresAt) {
    holdBatch(orderId, sessionId, seatIds, expiresAt, PREPARED);
  }

  private void holdBatch(String orderId, String sessionId, List<String> seatIds, Instant expiresAt, String status) {
    if (seatIds == null || seatIds.isEmpty()) return;
    List<InventoryLockEntity> rows = seatIds.stream().map(seatId -> {
      InventoryLockEntity lock = new InventoryLockEntity();
      lock.setId(UUID.randomUUID().toString()); lock.setOrderId(orderId); lock.setSessionId(sessionId);
      lock.setSeatId(seatId); lock.setActive(ACTIVE); lock.setStatus(status); lock.setExpiresAt(expiresAt);
      return lock;
    }).toList();
    locks.insertBatch(rows);
  }

  /** @return how many active locks were retired */
  public int retireActive(String orderId, String status) {
    return locks.retireActive(orderId, status);
  }

  public int promotePrepared(String orderId, Instant expiresAt) {
    return locks.promotePrepared(orderId, expiresAt);
  }

  public List<String> expiredOrderIds() {
    return locks.selectExpiredOrderIds();
  }

  public List<InventoryLockEntity> activeForSession(String sessionId) {
    return locks.selectList(Wrappers.<InventoryLockEntity>lambdaQuery()
        .eq(InventoryLockEntity::getSessionId, sessionId)
        .eq(InventoryLockEntity::getActive, ACTIVE)
        .gt(InventoryLockEntity::getExpiresAt, Instant.now()));
  }

  public List<String> confirmedStillLockedSeatIds(String sessionId) {
    return locks.selectConfirmedStillLockedSeatIds(sessionId);
  }
}

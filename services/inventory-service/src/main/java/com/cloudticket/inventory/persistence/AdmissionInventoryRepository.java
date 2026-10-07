package com.cloudticket.inventory.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cloudticket.inventory.persistence.entity.AdmissionInventoryEntity;
import com.cloudticket.inventory.persistence.mapper.AdmissionInventoryMapper;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Repository;

@Repository
public class AdmissionInventoryRepository {
  private final AdmissionInventoryMapper rows;

  public AdmissionInventoryRepository(AdmissionInventoryMapper rows) { this.rows = rows; }

  public AdmissionInventoryEntity lock(String sessionId) {
    AdmissionInventoryEntity row = rows.selectForUpdate(sessionId);
    if (row == null) throw new NoSuchElementException("admission inventory not found");
    return row;
  }

  public AdmissionInventoryEntity find(String sessionId) { return rows.selectById(sessionId); }

  public void ensure(String sessionId, int capacity) {
    if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("sessionId required");
    if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
    AdmissionInventoryEntity existing = rows.selectById(sessionId);
    if (existing != null) {
      int reserved = existing.getReservedCount() == null ? 0 : existing.getReservedCount();
      int sold = existing.getSoldCount() == null ? 0 : existing.getSoldCount();
      if (reserved > 0 || sold > 0) {
        if (existing.getCapacity() == null || existing.getCapacity() != capacity) {
          throw new IllegalStateException("admission capacity is immutable after reservation");
        }
        return;
      }
      rows.update(null, Wrappers.<AdmissionInventoryEntity>lambdaUpdate()
          .set(AdmissionInventoryEntity::getCapacity, capacity)
          .eq(AdmissionInventoryEntity::getSessionId, sessionId));
      return;
    }
    AdmissionInventoryEntity row = new AdmissionInventoryEntity();
    row.setSessionId(sessionId);
    row.setCapacity(capacity);
    row.setReservedCount(0);
    row.setSoldCount(0);
    row.setNextTicketNumber(1L);
    row.setVersion(0L);
    rows.insert(row);
  }

  public void save(String sessionId, int capacity, int reserved, int sold, long nextTicketNumber, long version) {
    rows.update(null, Wrappers.<AdmissionInventoryEntity>lambdaUpdate()
        .set(AdmissionInventoryEntity::getCapacity, capacity)
        .set(AdmissionInventoryEntity::getReservedCount, reserved)
        .set(AdmissionInventoryEntity::getSoldCount, sold)
        .set(AdmissionInventoryEntity::getNextTicketNumber, nextTicketNumber)
        .set(AdmissionInventoryEntity::getVersion, version)
        .eq(AdmissionInventoryEntity::getSessionId, sessionId));
  }

  public void moveReservedToSold(String sessionId, int quantity) {
    if (quantity <= 0) return;
    rows.update(null, Wrappers.<AdmissionInventoryEntity>lambdaUpdate()
        .setSql("reserved_count = reserved_count - " + quantity)
        .setSql("sold_count = sold_count + " + quantity)
        .eq(AdmissionInventoryEntity::getSessionId, sessionId)
        .ge(AdmissionInventoryEntity::getReservedCount, quantity));
  }

  public void releaseReserved(String sessionId, int quantity) {
    if (quantity <= 0) return;
    rows.update(null, Wrappers.<AdmissionInventoryEntity>lambdaUpdate()
        .setSql("reserved_count = reserved_count - " + quantity)
        .eq(AdmissionInventoryEntity::getSessionId, sessionId)
        .ge(AdmissionInventoryEntity::getReservedCount, quantity));
  }

  public void releaseSold(String sessionId, int quantity) {
    if (quantity <= 0) return;
    rows.update(null, Wrappers.<AdmissionInventoryEntity>lambdaUpdate()
        .setSql("sold_count = sold_count - " + quantity)
        .eq(AdmissionInventoryEntity::getSessionId, sessionId)
        .ge(AdmissionInventoryEntity::getSoldCount, quantity));
  }
}

package com.cloudticket.inventory.persistence;

import com.cloudticket.inventory.persistence.entity.InventoryReconciliationDiffEntity;
import com.cloudticket.inventory.persistence.mapper.InventoryReconciliationDiffMapper;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryReconciliationDiffRepository {
  private final InventoryReconciliationDiffMapper rows;
  public InventoryReconciliationDiffRepository(InventoryReconciliationDiffMapper rows) { this.rows = rows; }
  public void open(String reservationId, String sessionId, String type, String before, String after, String reason) {
    InventoryReconciliationDiffEntity row = new InventoryReconciliationDiffEntity();
    row.setReservationId(reservationId); row.setSessionId(sessionId); row.setDiffType(type);
    row.setBeforeState(before); row.setAfterState(after); row.setRepairStatus("REPAIRED"); row.setReason(reason);
    row.setRepairedAt(java.time.Instant.now()); rows.insert(row);
  }
}

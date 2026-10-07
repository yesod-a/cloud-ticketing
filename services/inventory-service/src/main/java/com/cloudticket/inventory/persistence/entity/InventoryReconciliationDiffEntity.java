package com.cloudticket.inventory.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("inventory_reconciliation_diff")
public class InventoryReconciliationDiffEntity {
  @TableId(value = "id", type = IdType.AUTO) private Long id;
  private String reservationId, sessionId, diffType, beforeState, afterState, repairStatus, reason;
  private Instant createdAt, repairedAt;
  public Long getId() { return id; } public void setId(Long v) { id = v; }
  public String getReservationId() { return reservationId; } public void setReservationId(String v) { reservationId = v; }
  public String getSessionId() { return sessionId; } public void setSessionId(String v) { sessionId = v; }
  public String getDiffType() { return diffType; } public void setDiffType(String v) { diffType = v; }
  public String getBeforeState() { return beforeState; } public void setBeforeState(String v) { beforeState = v; }
  public String getAfterState() { return afterState; } public void setAfterState(String v) { afterState = v; }
  public String getRepairStatus() { return repairStatus; } public void setRepairStatus(String v) { repairStatus = v; }
  public String getReason() { return reason; } public void setReason(String v) { reason = v; }
  public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant v) { createdAt = v; }
  public Instant getRepairedAt() { return repairedAt; } public void setRepairedAt(Instant v) { repairedAt = v; }
}

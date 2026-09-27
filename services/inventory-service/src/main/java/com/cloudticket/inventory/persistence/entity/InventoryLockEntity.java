package com.cloudticket.inventory.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** Row of {@code inventory_lock}. An active row is one held seat of one order. */
@TableName("inventory_lock")
public class InventoryLockEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  private String orderId;
  private String sessionId;
  private String seatId;
  private Integer active;
  private String status;
  private Instant expiresAt;

  @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
  private Instant createdAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getOrderId() { return orderId; }
  public void setOrderId(String orderId) { this.orderId = orderId; }
  public String getSessionId() { return sessionId; }
  public void setSessionId(String sessionId) { this.sessionId = sessionId; }
  public String getSeatId() { return seatId; }
  public void setSeatId(String seatId) { this.seatId = seatId; }
  public Integer getActive() { return active; }
  public void setActive(Integer active) { this.active = active; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

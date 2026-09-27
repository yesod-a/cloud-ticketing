package com.cloudticket.order.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** Row of {@code ticket_order}. */
@TableName("ticket_order")
public class TicketOrderEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  private String userId;
  private String sessionId;
  private String seatIds;
  private String idempotencyKey;
  private String requestHash;
  private String status;
  private Integer amountMinor;

  // Both columns are maintained by the database (DEFAULT / ON UPDATE), so they are never written.
  @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
  private Instant createdAt;
  @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
  private Instant updatedAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  public String getSessionId() { return sessionId; }
  public void setSessionId(String sessionId) { this.sessionId = sessionId; }
  public String getSeatIds() { return seatIds; }
  public void setSeatIds(String seatIds) { this.seatIds = seatIds; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
  public String getRequestHash() { return requestHash; }
  public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Integer getAmountMinor() { return amountMinor; }
  public void setAmountMinor(Integer amountMinor) { this.amountMinor = amountMinor; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

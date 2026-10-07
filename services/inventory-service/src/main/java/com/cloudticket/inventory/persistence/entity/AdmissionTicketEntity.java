package com.cloudticket.inventory.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("admission_ticket")
public class AdmissionTicketEntity {
  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  private String sessionId;
  private String orderId;
  private String userId;
  private Long ticketNumber;
  private String state;
  private Instant expiresAt;
  @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
  private Instant createdAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getSessionId() { return sessionId; }
  public void setSessionId(String sessionId) { this.sessionId = sessionId; }
  public String getOrderId() { return orderId; }
  public void setOrderId(String orderId) { this.orderId = orderId; }
  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  public Long getTicketNumber() { return ticketNumber; }
  public void setTicketNumber(Long ticketNumber) { this.ticketNumber = ticketNumber; }
  public String getState() { return state; }
  public void setState(String state) { this.state = state; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

package com.cloudticket.inventory.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** Row of {@code inventory_audit_log}; every manual seat change records why it happened. */
@TableName("inventory_audit_log")
public class InventoryAuditLogEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  private String actorUserId;
  private String action;
  private String resourceId;
  private String beforeStatus;
  private String afterStatus;
  private String reason;
  private String traceId;

  @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
  private Instant createdAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getActorUserId() { return actorUserId; }
  public void setActorUserId(String actorUserId) { this.actorUserId = actorUserId; }
  public String getAction() { return action; }
  public void setAction(String action) { this.action = action; }
  public String getResourceId() { return resourceId; }
  public void setResourceId(String resourceId) { this.resourceId = resourceId; }
  public String getBeforeStatus() { return beforeStatus; }
  public void setBeforeStatus(String beforeStatus) { this.beforeStatus = beforeStatus; }
  public String getAfterStatus() { return afterStatus; }
  public void setAfterStatus(String afterStatus) { this.afterStatus = afterStatus; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public String getTraceId() { return traceId; }
  public void setTraceId(String traceId) { this.traceId = traceId; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

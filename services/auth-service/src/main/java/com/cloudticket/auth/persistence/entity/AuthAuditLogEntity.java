package com.cloudticket.auth.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.UUID;

/** Row of {@code auth_audit_log}; the table rejects UPDATE and DELETE by trigger. */
@TableName("auth_audit_log")
public class AuthAuditLogEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private UUID id;
  private UUID actorUserId;
  private String action;
  private String resourceType;
  private UUID resourceId;
  private String beforeJson;
  private String afterJson;
  private String traceId;

  @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
  private Instant createdAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getActorUserId() { return actorUserId; }
  public void setActorUserId(UUID actorUserId) { this.actorUserId = actorUserId; }
  public String getAction() { return action; }
  public void setAction(String action) { this.action = action; }
  public String getResourceType() { return resourceType; }
  public void setResourceType(String resourceType) { this.resourceType = resourceType; }
  public UUID getResourceId() { return resourceId; }
  public void setResourceId(UUID resourceId) { this.resourceId = resourceId; }
  public String getBeforeJson() { return beforeJson; }
  public void setBeforeJson(String beforeJson) { this.beforeJson = beforeJson; }
  public String getAfterJson() { return afterJson; }
  public void setAfterJson(String afterJson) { this.afterJson = afterJson; }
  public String getTraceId() { return traceId; }
  public void setTraceId(String traceId) { this.traceId = traceId; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

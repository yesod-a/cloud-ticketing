package com.cloudticket.activity.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** Row of {@code activity_audit_log}. */
@TableName("activity_audit_log")
public class ActivityAuditLogEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  private String actorUserId;
  private String action;
  private String resourceType;
  private String resourceId;
  private String beforeJson;
  private String afterJson;
  private String traceId;

  @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
  private Instant createdAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getActorUserId() { return actorUserId; }
  public void setActorUserId(String actorUserId) { this.actorUserId = actorUserId; }
  public String getAction() { return action; }
  public void setAction(String action) { this.action = action; }
  public String getResourceType() { return resourceType; }
  public void setResourceType(String resourceType) { this.resourceType = resourceType; }
  public String getResourceId() { return resourceId; }
  public void setResourceId(String resourceId) { this.resourceId = resourceId; }
  public String getBeforeJson() { return beforeJson; }
  public void setBeforeJson(String beforeJson) { this.beforeJson = beforeJson; }
  public String getAfterJson() { return afterJson; }
  public void setAfterJson(String afterJson) { this.afterJson = afterJson; }
  public String getTraceId() { return traceId; }
  public void setTraceId(String traceId) { this.traceId = traceId; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

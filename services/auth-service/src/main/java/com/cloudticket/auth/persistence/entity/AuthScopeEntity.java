package com.cloudticket.auth.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.UUID;

/** Row of {@code auth_scope}: one operator grant over one resource. */
@TableName("auth_scope")
public class AuthScopeEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private UUID id;
  private String resourceType;
  private UUID resourceId;
  private String status;

  @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
  private Instant createdAt;
  /** Denormalised for the admin listing; not a column of {@code auth_scope}. */
  @TableField(exist = false)
  private Long userCount;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getResourceType() { return resourceType; }
  public void setResourceType(String resourceType) { this.resourceType = resourceType; }
  public UUID getResourceId() { return resourceId; }
  public void setResourceId(UUID resourceId) { this.resourceId = resourceId; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Long getUserCount() { return userCount; }
  public void setUserCount(Long userCount) { this.userCount = userCount; }
}

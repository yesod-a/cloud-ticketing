package com.cloudticket.auth.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.UUID;

/**
 * Row of {@code auth_user}.
 *
 * <p>Identifiers are {@code BINARY(16)}; the service registers a UUID type handler so the mapper can
 * use {@code WHERE id = #{id}} instead of hand-written {@code UUID_TO_BIN} calls.
 */
@TableName("auth_user")
public class AuthUserEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private UUID id;
  private String phone;
  private String email;
  private String passwordHash;
  private String nickname;
  private String status;
  private Integer failedLoginCount;
  private Instant lockedUntil;
  private Long scopeVersion;

  @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
  private Instant createdAt;
  @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
  private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getPasswordHash() { return passwordHash; }
  public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
  public String getNickname() { return nickname; }
  public void setNickname(String nickname) { this.nickname = nickname; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Integer getFailedLoginCount() { return failedLoginCount; }
  public void setFailedLoginCount(Integer failedLoginCount) { this.failedLoginCount = failedLoginCount; }
  public Instant getLockedUntil() { return lockedUntil; }
  public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
  public Long getScopeVersion() { return scopeVersion; }
  public void setScopeVersion(Long scopeVersion) { this.scopeVersion = scopeVersion; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

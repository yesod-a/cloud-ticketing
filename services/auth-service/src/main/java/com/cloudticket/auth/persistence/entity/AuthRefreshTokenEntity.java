package com.cloudticket.auth.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.UUID;

/** Row of {@code auth_refresh_token}. */
@TableName("auth_refresh_token")
public class AuthRefreshTokenEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private UUID id;
  private UUID userId;
  private String tokenHash;
  private UUID familyId;
  private Instant expiresAt;
  private Instant revokedAt;
  private UUID replacedById;

  @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
  private Instant createdAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }
  public String getTokenHash() { return tokenHash; }
  public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
  public UUID getFamilyId() { return familyId; }
  public void setFamilyId(UUID familyId) { this.familyId = familyId; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
  public Instant getRevokedAt() { return revokedAt; }
  public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
  public UUID getReplacedById() { return replacedById; }
  public void setReplacedById(UUID replacedById) { this.replacedById = replacedById; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

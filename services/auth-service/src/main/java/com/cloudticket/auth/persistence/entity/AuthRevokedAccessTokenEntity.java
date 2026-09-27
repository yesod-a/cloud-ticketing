package com.cloudticket.auth.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** Row of {@code auth_revoked_access_token}: access tokens revoked before their natural expiry. */
@TableName("auth_revoked_access_token")
public class AuthRevokedAccessTokenEntity {

  @TableId(value = "jti", type = IdType.INPUT)
  private String jti;
  private Instant expiresAt;

  @TableField(value = "revoked_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
  private Instant revokedAt;

  public String getJti() { return jti; }
  public void setJti(String jti) { this.jti = jti; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
  public Instant getRevokedAt() { return revokedAt; }
  public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
}

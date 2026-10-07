package com.cloudticket.inventory.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("inventory_cache_warmup")
public class InventoryCacheWarmupEntity {
  @TableId(value = "session_id", type = IdType.INPUT)
  private String sessionId;
  private Instant preheatAt;
  private String status;
  private String cacheVersion;
  private Integer attempts;
  private Instant nextAttemptAt;
  private String lastError;
  private String claimedBy;
  private Instant claimUntil;
  private Instant preheatedAt;
  private Instant createdAt;
  private Instant updatedAt;

  public String getSessionId() { return sessionId; }
  public void setSessionId(String value) { sessionId = value; }
  public Instant getPreheatAt() { return preheatAt; }
  public void setPreheatAt(Instant value) { preheatAt = value; }
  public String getStatus() { return status; }
  public void setStatus(String value) { status = value; }
  public String getCacheVersion() { return cacheVersion; }
  public void setCacheVersion(String value) { cacheVersion = value; }
  public Integer getAttempts() { return attempts; }
  public void setAttempts(Integer value) { attempts = value; }
  public Instant getNextAttemptAt() { return nextAttemptAt; }
  public void setNextAttemptAt(Instant value) { nextAttemptAt = value; }
  public String getLastError() { return lastError; }
  public void setLastError(String value) { lastError = value; }
  public String getClaimedBy() { return claimedBy; }
  public void setClaimedBy(String value) { claimedBy = value; }
  public Instant getClaimUntil() { return claimUntil; }
  public void setClaimUntil(Instant value) { claimUntil = value; }
  public Instant getPreheatedAt() { return preheatedAt; }
  public void setPreheatedAt(Instant value) { preheatedAt = value; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant value) { createdAt = value; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant value) { updatedAt = value; }
}

package com.cloudticket.order.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** Row of {@code order_outbox}. */
@TableName("order_outbox")
public class OrderOutboxEntity {

  @TableId(value = "event_id", type = IdType.INPUT)
  private String eventId;
  private String eventType;
  private String aggregateType;
  private String aggregateId;
  private String payload;
  private String traceId;
  private Integer schemaVersion;

  @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
  private Instant occurredAt;
  @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
  private Instant publishedAt;
  @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
  private Integer attempts;
  @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
  private String lastError;
  private String claimToken;
  private Instant claimedUntil;
  private Instant nextAttemptAt;

  public String getEventId() { return eventId; }
  public void setEventId(String eventId) { this.eventId = eventId; }
  public String getEventType() { return eventType; }
  public void setEventType(String eventType) { this.eventType = eventType; }
  public String getAggregateType() { return aggregateType; }
  public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
  public String getAggregateId() { return aggregateId; }
  public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
  public String getPayload() { return payload; }
  public void setPayload(String payload) { this.payload = payload; }
  public String getTraceId() { return traceId; }
  public void setTraceId(String traceId) { this.traceId = traceId; }
  public Integer getSchemaVersion() { return schemaVersion; }
  public void setSchemaVersion(Integer schemaVersion) { this.schemaVersion = schemaVersion; }
  public Instant getOccurredAt() { return occurredAt; }
  public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
  public Instant getPublishedAt() { return publishedAt; }
  public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
  public Integer getAttempts() { return attempts; }
  public void setAttempts(Integer attempts) { this.attempts = attempts; }
  public String getLastError() { return lastError; }
  public void setLastError(String lastError) { this.lastError = lastError; }
  public String getClaimToken() { return claimToken; }
  public void setClaimToken(String claimToken) { this.claimToken = claimToken; }
  public Instant getClaimedUntil() { return claimedUntil; }
  public void setClaimedUntil(Instant claimedUntil) { this.claimedUntil = claimedUntil; }
  public Instant getNextAttemptAt() { return nextAttemptAt; }
  public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
}

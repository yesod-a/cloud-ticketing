package com.cloudticket.order.timeout;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("order_timeout_outbox")
public class OrderTimeoutOutboxEntity {
  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  private String orderId;
  private Instant expireAt;
  private Instant publishedAt;
  private Integer attempts;
  private String lastError;
  private Instant nextAttemptAt;
  private Instant createdAt;

  public String getId() { return id; }
  public void setId(String value) { id = value; }
  public String getOrderId() { return orderId; }
  public void setOrderId(String value) { orderId = value; }
  public Instant getExpireAt() { return expireAt; }
  public void setExpireAt(Instant value) { expireAt = value; }
  public Instant getPublishedAt() { return publishedAt; }
  public void setPublishedAt(Instant value) { publishedAt = value; }
  public Integer getAttempts() { return attempts; }
  public void setAttempts(Integer value) { attempts = value; }
  public String getLastError() { return lastError; }
  public void setLastError(String value) { lastError = value; }
  public Instant getNextAttemptAt() { return nextAttemptAt; }
  public void setNextAttemptAt(Instant value) { nextAttemptAt = value; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant value) { createdAt = value; }
}

package com.cloudticket.order.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** Row of {@code payment}: one simulated payment intent per order. */
@TableName("payment")
public class PaymentEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  private String orderId;
  private String userId;
  private String method;
  private Integer amountMinor;
  private String currency;
  private String status;
  private String qrToken;
  private String providerTransactionId;

  @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
  private Instant createdAt;
  private Instant paidAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getOrderId() { return orderId; }
  public void setOrderId(String orderId) { this.orderId = orderId; }
  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  public String getMethod() { return method; }
  public void setMethod(String method) { this.method = method; }
  public Integer getAmountMinor() { return amountMinor; }
  public void setAmountMinor(Integer amountMinor) { this.amountMinor = amountMinor; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getQrToken() { return qrToken; }
  public void setQrToken(String qrToken) { this.qrToken = qrToken; }
  public String getProviderTransactionId() { return providerTransactionId; }
  public void setProviderTransactionId(String providerTransactionId) { this.providerTransactionId = providerTransactionId; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getPaidAt() { return paidAt; }
  public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
}

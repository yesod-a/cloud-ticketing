package com.cloudticket.order.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("user_session_purchase_reservation")
public class UserSessionPurchaseReservationEntity {
  @TableId private String orderId;
  private String userId;
  private String sessionId;
  private Integer quantity;
  private String state;
  private Instant expiresAt;
  public String getOrderId() { return orderId; }
  public void setOrderId(String value) { orderId = value; }
  public String getUserId() { return userId; }
  public void setUserId(String value) { userId = value; }
  public String getSessionId() { return sessionId; }
  public void setSessionId(String value) { sessionId = value; }
  public Integer getQuantity() { return quantity; }
  public void setQuantity(Integer value) { quantity = value; }
  public String getState() { return state; }
  public void setState(String value) { state = value; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant value) { expiresAt = value; }
}

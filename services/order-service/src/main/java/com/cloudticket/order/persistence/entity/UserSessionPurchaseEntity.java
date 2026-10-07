package com.cloudticket.order.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("user_session_purchase")
public class UserSessionPurchaseEntity {
  @TableId private String userId;
  private String sessionId;
  private Integer activeQuantity;
  private Integer reservedQuantity;
  private Instant reservationExpiresAt;
  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  public String getSessionId() { return sessionId; }
  public void setSessionId(String sessionId) { this.sessionId = sessionId; }
  public Integer getActiveQuantity() { return activeQuantity; }
  public void setActiveQuantity(Integer value) { activeQuantity = value; }
  public Integer getReservedQuantity() { return reservedQuantity; }
  public void setReservedQuantity(Integer value) { reservedQuantity = value; }
  public Instant getReservationExpiresAt() { return reservationExpiresAt; }
  public void setReservationExpiresAt(Instant value) { reservationExpiresAt = value; }
}

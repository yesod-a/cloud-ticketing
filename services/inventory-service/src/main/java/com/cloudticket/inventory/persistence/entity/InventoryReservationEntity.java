package com.cloudticket.inventory.persistence.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;
@TableName("inventory_reservation")
public class InventoryReservationEntity {
  @TableId(value="reservation_id",type=IdType.INPUT) private String reservationId;
  private String orderId,sessionId,userId,mode,seatIndexes,seatIds,ticketNumbers,status,lastError; private Integer quantity,attempts; private Instant expiresAt,nextAttemptAt;
  @TableField(value="created_at",insertStrategy=FieldStrategy.NEVER,updateStrategy=FieldStrategy.NEVER) private Instant createdAt;
  @TableField(value="updated_at",insertStrategy=FieldStrategy.NEVER,updateStrategy=FieldStrategy.NEVER) private Instant updatedAt;
  public String getReservationId(){return reservationId;} public void setReservationId(String v){reservationId=v;}
  public String getOrderId(){return orderId;} public void setOrderId(String v){orderId=v;} public String getSessionId(){return sessionId;} public void setSessionId(String v){sessionId=v;} public String getUserId(){return userId;} public void setUserId(String v){userId=v;} public String getMode(){return mode;} public void setMode(String v){mode=v;} public String getSeatIndexes(){return seatIndexes;} public void setSeatIndexes(String v){seatIndexes=v;} public String getSeatIds(){return seatIds;} public void setSeatIds(String v){seatIds=v;} public String getTicketNumbers(){return ticketNumbers;} public void setTicketNumbers(String v){ticketNumbers=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public Integer getQuantity(){return quantity;} public void setQuantity(Integer v){quantity=v;} public Integer getAttempts(){return attempts;} public void setAttempts(Integer v){attempts=v;} public Instant getExpiresAt(){return expiresAt;} public void setExpiresAt(Instant v){expiresAt=v;} public Instant getNextAttemptAt(){return nextAttemptAt;} public void setNextAttemptAt(Instant v){nextAttemptAt=v;} public String getLastError(){return lastError;} public void setLastError(String v){lastError=v;}
}

package com.cloudticket.inventory.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;

/** Row of {@code inventory_seat}: the sellable seat, owned by this service. */
@TableName("inventory_seat")
public class InventorySeatEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  private String sessionId;
  private String activityId;
  private String areaLabel;
  private String rowLabel;
  private Integer seatNumber;
  private String displayName;
  private String seatType;
  private Integer seatIndex;
  private BigDecimal positionX;
  private BigDecimal positionY;
  private String status;

  @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
  private Instant updatedAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getSessionId() { return sessionId; }
  public void setSessionId(String sessionId) { this.sessionId = sessionId; }
  public String getActivityId() { return activityId; }
  public void setActivityId(String activityId) { this.activityId = activityId; }
  public String getAreaLabel() { return areaLabel; }
  public void setAreaLabel(String areaLabel) { this.areaLabel = areaLabel; }
  public String getRowLabel() { return rowLabel; }
  public void setRowLabel(String rowLabel) { this.rowLabel = rowLabel; }
  public Integer getSeatNumber() { return seatNumber; }
  public void setSeatNumber(Integer seatNumber) { this.seatNumber = seatNumber; }
  public String getDisplayName() { return displayName; }
  public void setDisplayName(String displayName) { this.displayName = displayName; }
  public String getSeatType() { return seatType; }
  public void setSeatType(String seatType) { this.seatType = seatType; }
  public Integer getSeatIndex() { return seatIndex; }
  public void setSeatIndex(Integer seatIndex) { this.seatIndex = seatIndex; }
  public BigDecimal getPositionX() { return positionX; }
  public void setPositionX(BigDecimal positionX) { this.positionX = positionX; }
  public BigDecimal getPositionY() { return positionY; }
  public void setPositionY(BigDecimal positionY) { this.positionY = positionY; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

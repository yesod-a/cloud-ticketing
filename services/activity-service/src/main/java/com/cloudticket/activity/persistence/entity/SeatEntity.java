package com.cloudticket.activity.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;

/** Row of {@code seat}: the per-session snapshot of a venue seat. */
@TableName("seat")
public class SeatEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  private String sessionId;
  private String areaLabel;
  private String rowLabel;
  private Integer seatNumber;
  private String displayName;
  private String seatType;
  private BigDecimal positionX;
  private BigDecimal positionY;
  private String status;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getSessionId() { return sessionId; }
  public void setSessionId(String sessionId) { this.sessionId = sessionId; }
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
  public BigDecimal getPositionX() { return positionX; }
  public void setPositionX(BigDecimal positionX) { this.positionX = positionX; }
  public BigDecimal getPositionY() { return positionY; }
  public void setPositionY(BigDecimal positionY) { this.positionY = positionY; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
}

package com.cloudticket.activity.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;

/** Row of {@code venue_seat}: the reusable seat template of a venue. */
@TableName("venue_seat")
public class VenueSeatEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  private String venueId;
  private String areaLabel;
  private String rowLabel;
  private Integer seatNumber;
  private String displayName;
  private BigDecimal positionX;
  private BigDecimal positionY;
  private String seatType;
  private Boolean enabled;
  private String status;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getVenueId() { return venueId; }
  public void setVenueId(String venueId) { this.venueId = venueId; }
  public String getAreaLabel() { return areaLabel; }
  public void setAreaLabel(String areaLabel) { this.areaLabel = areaLabel; }
  public String getRowLabel() { return rowLabel; }
  public void setRowLabel(String rowLabel) { this.rowLabel = rowLabel; }
  public Integer getSeatNumber() { return seatNumber; }
  public void setSeatNumber(Integer seatNumber) { this.seatNumber = seatNumber; }
  public String getDisplayName() { return displayName; }
  public void setDisplayName(String displayName) { this.displayName = displayName; }
  public BigDecimal getPositionX() { return positionX; }
  public void setPositionX(BigDecimal positionX) { this.positionX = positionX; }
  public BigDecimal getPositionY() { return positionY; }
  public void setPositionY(BigDecimal positionY) { this.positionY = positionY; }
  public String getSeatType() { return seatType; }
  public void setSeatType(String seatType) { this.seatType = seatType; }
  public Boolean getEnabled() { return enabled; }
  public void setEnabled(Boolean enabled) { this.enabled = enabled; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
}

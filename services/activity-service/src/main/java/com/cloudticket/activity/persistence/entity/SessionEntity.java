package com.cloudticket.activity.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** Row of {@code activity_session}. */
@TableName("activity_session")
public class SessionEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  private String activityId;
  private String venueId;
  private Instant startsAt;
  private Instant endsAt;
  private String status;
  private Integer priceMinor;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getActivityId() { return activityId; }
  public void setActivityId(String activityId) { this.activityId = activityId; }
  public String getVenueId() { return venueId; }
  public void setVenueId(String venueId) { this.venueId = venueId; }
  public Instant getStartsAt() { return startsAt; }
  public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
  public Instant getEndsAt() { return endsAt; }
  public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Integer getPriceMinor() { return priceMinor; }
  public void setPriceMinor(Integer priceMinor) { this.priceMinor = priceMinor; }
}

package com.cloudticket.activity.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** Row of {@code venue}. {@code activity_id} is nullable since venues became standalone. */
@TableName("venue")
public class VenueEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  private String activityId;
  private String name;
  private String address;
  private Integer capacity;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getActivityId() { return activityId; }
  public void setActivityId(String activityId) { this.activityId = activityId; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getAddress() { return address; }
  public void setAddress(String address) { this.address = address; }
  public Integer getCapacity() { return capacity; }
  public void setCapacity(Integer capacity) { this.capacity = capacity; }
}

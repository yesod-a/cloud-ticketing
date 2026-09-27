package com.cloudticket.activity.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** Row of {@code activity}. */
@TableName("activity")
public class ActivityEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  private String title;
  private String organizer;
  private String status;
  private Boolean layoutFrozen;

  @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
  private Instant createdAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getOrganizer() { return organizer; }
  public void setOrganizer(String organizer) { this.organizer = organizer; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Boolean getLayoutFrozen() { return layoutFrozen; }
  public void setLayoutFrozen(Boolean layoutFrozen) { this.layoutFrozen = layoutFrozen; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

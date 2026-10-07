package com.cloudticket.activity.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** Metadata for an activity image; the binary is held by the configured object storage. */
@TableName("activity_image")
public class ActivityImageEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  private String activityId;
  private String objectKey;
  private String originalName;
  private String contentType;
  private Long sizeBytes;
  private String imageType;
  private Integer sortOrder;
  private String status;
  private Instant createdAt;
  private Instant updatedAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getActivityId() { return activityId; }
  public void setActivityId(String activityId) { this.activityId = activityId; }
  public String getObjectKey() { return objectKey; }
  public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
  public String getOriginalName() { return originalName; }
  public void setOriginalName(String originalName) { this.originalName = originalName; }
  public String getContentType() { return contentType; }
  public void setContentType(String contentType) { this.contentType = contentType; }
  public Long getSizeBytes() { return sizeBytes; }
  public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
  public String getImageType() { return imageType; }
  public void setImageType(String imageType) { this.imageType = imageType; }
  public Integer getSortOrder() { return sortOrder; }
  public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

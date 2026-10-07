package com.cloudticket.inventory.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("admission_inventory")
public class AdmissionInventoryEntity {
  @TableId(value = "session_id", type = IdType.INPUT)
  private String sessionId;
  private Integer capacity;
  private Integer reservedCount;
  private Integer soldCount;
  private Long nextTicketNumber;
  private Long version;
  @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
  private Instant updatedAt;

  public String getSessionId() { return sessionId; }
  public void setSessionId(String sessionId) { this.sessionId = sessionId; }
  public Integer getCapacity() { return capacity; }
  public void setCapacity(Integer capacity) { this.capacity = capacity; }
  public Integer getReservedCount() { return reservedCount; }
  public void setReservedCount(Integer reservedCount) { this.reservedCount = reservedCount; }
  public Integer getSoldCount() { return soldCount; }
  public void setSoldCount(Integer soldCount) { this.soldCount = soldCount; }
  public Long getNextTicketNumber() { return nextTicketNumber; }
  public void setNextTicketNumber(Long nextTicketNumber) { this.nextTicketNumber = nextTicketNumber; }
  public Long getVersion() { return version; }
  public void setVersion(Long version) { this.version = version; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

package com.cloudticket.inventory.persistence.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;
@TableName("inventory_outbox") public class InventoryOutboxEntity {
  @TableId(value="event_id",type=IdType.INPUT) private String eventId; private String topic,eventKey,payload,lastError; private Integer attempts;
  private Instant publishedAt,nextAttemptAt;
  @TableField(value="created_at",insertStrategy=FieldStrategy.NEVER,updateStrategy=FieldStrategy.NEVER) private Instant createdAt;
  public String getEventId(){return eventId;} public void setEventId(String v){eventId=v;} public String getTopic(){return topic;} public void setTopic(String v){topic=v;} public String getEventKey(){return eventKey;} public void setEventKey(String v){eventKey=v;} public String getPayload(){return payload;} public void setPayload(String v){payload=v;} public String getLastError(){return lastError;} public void setLastError(String v){lastError=v;} public Integer getAttempts(){return attempts;} public void setAttempts(Integer v){attempts=v;} public Instant getPublishedAt(){return publishedAt;} public void setPublishedAt(Instant v){publishedAt=v;} public Instant getNextAttemptAt(){return nextAttemptAt;} public void setNextAttemptAt(Instant v){nextAttemptAt=v;} public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
}

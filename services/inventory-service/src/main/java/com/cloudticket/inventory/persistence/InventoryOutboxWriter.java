package com.cloudticket.inventory.persistence;
import com.cloudticket.inventory.persistence.entity.InventoryOutboxEntity;
import com.cloudticket.inventory.persistence.mapper.InventoryOutboxMapper;
import java.util.UUID; import org.springframework.stereotype.Component;
@Component public class InventoryOutboxWriter {
  private final InventoryOutboxMapper rows; public InventoryOutboxWriter(InventoryOutboxMapper rows){this.rows=rows;}
  public void write(String topic,String key,String payload){InventoryOutboxEntity row=new InventoryOutboxEntity();row.setEventId(UUID.randomUUID().toString());row.setTopic(topic);row.setEventKey(key);row.setPayload(payload);row.setAttempts(0);rows.insert(row);}
}

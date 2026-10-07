package com.cloudticket.inventory.event;
import com.cloudticket.inventory.persistence.entity.InventoryOutboxEntity;
import com.cloudticket.inventory.persistence.mapper.InventoryOutboxMapper;
import java.time.Instant; import java.util.List;
import org.springframework.beans.factory.annotation.Value; import org.springframework.kafka.core.KafkaTemplate; import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Component;
@Component public class InventoryOutboxPublisher {
  private final InventoryOutboxMapper rows; private final KafkaTemplate<String,String> kafka; private final boolean enabled;
  public InventoryOutboxPublisher(InventoryOutboxMapper rows,KafkaTemplate<String,String> kafka,@Value("${cloudticket.queued.outbox-enabled:false}") boolean enabled){this.rows=rows;this.kafka=kafka;this.enabled=enabled;}
  @Scheduled(fixedDelayString="${cloudticket.queued.outbox-poll-ms:500}") public void publishOnce(){if(!enabled)return;List<InventoryOutboxEntity> pending=rows.pending(50);for(InventoryOutboxEntity row:pending){try{kafka.send(row.getTopic(),row.getEventKey(),row.getPayload()).whenComplete((ok,error)->{if(error==null)rows.markPublished(row.getEventId());else rows.markFailure(row.getEventId(),error.getMessage(),Instant.now().plusSeconds(1));});}catch(Exception e){rows.markFailure(row.getEventId(),e.getMessage(),Instant.now().plusSeconds(1));}}}
}

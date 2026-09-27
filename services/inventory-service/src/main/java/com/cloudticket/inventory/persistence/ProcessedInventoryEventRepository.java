package com.cloudticket.inventory.persistence;

import com.cloudticket.inventory.persistence.mapper.ProcessedInventoryEventMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessedInventoryEventRepository {

  private final ProcessedInventoryEventMapper events;

  public ProcessedInventoryEventRepository(ProcessedInventoryEventMapper events) {
    this.events = events;
  }

  public boolean tryClaim(String eventId, String eventType, String aggregateId, String traceId) {
    try {
      events.insert(eventId, eventType, aggregateId, traceId);
      return true;
    } catch (DuplicateKeyException duplicate) {
      return false;
    }
  }
}

package com.cloudticket.order.persistence;

import com.cloudticket.order.persistence.mapper.ProcessedEventMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Durable consumer-side idempotency guard for order event consumers. */
@Repository
public class ProcessedEventRepository {

  private final ProcessedEventMapper events;

  public ProcessedEventRepository(ProcessedEventMapper events) {
    this.events = events;
  }

  /** Claims the event for this consumer; the duplicate-key insert is the atomic claim. */
  @Transactional
  public boolean tryClaim(String eventId, String consumerName, String eventType,
                          String aggregateId, String payloadHash, String traceId) {
    try {
      events.claim(eventId, consumerName, eventType, aggregateId, payloadHash, traceId);
      return true;
    } catch (DuplicateKeyException duplicate) {
      return false;
    }
  }
}

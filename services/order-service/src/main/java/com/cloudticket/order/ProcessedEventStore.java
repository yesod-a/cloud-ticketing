package com.cloudticket.order;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Durable consumer-side idempotency guard for future Kafka consumers. */
@Repository
public class ProcessedEventStore {
  private final JdbcTemplate jdbc;

  public ProcessedEventStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public boolean tryClaim(String eventId, String consumerName, String eventType,
                          String aggregateId, String payloadHash, String traceId) {
    try {
      jdbc.update("INSERT INTO processed_event(event_id,consumer_name,event_type,aggregate_id,payload_hash,trace_id) VALUES (?,?,?,?,?,?)",
          eventId, consumerName, eventType, aggregateId, payloadHash, traceId);
      return true;
    } catch (DuplicateKeyException duplicate) {
      return false;
    }
  }
}

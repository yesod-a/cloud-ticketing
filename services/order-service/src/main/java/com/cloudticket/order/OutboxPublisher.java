package com.cloudticket.order;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Publishes durable order events without participating in the order write transaction. */
@Component
@ConditionalOnProperty(prefix = "cloudticket.outbox", name = "enabled", havingValue = "true")
public class OutboxPublisher {
  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

  private final JdbcTemplate jdbc;
  private final KafkaTemplate<String, String> kafka;
  private final String topic;
  private final int batchSize;
  private final long sendTimeoutMs;

  @Autowired
  public OutboxPublisher(
      JdbcTemplate jdbc,
      KafkaTemplate<String, String> kafka,
      @Value("${cloudticket.outbox.topic:order-events}") String topic,
      @Value("${cloudticket.outbox.batch-size:50}") int batchSize,
      @Value("${cloudticket.outbox.send-timeout-ms:1000}") long sendTimeoutMs) {
    this.jdbc = jdbc;
    this.kafka = kafka;
    this.topic = topic;
    this.batchSize = Math.max(1, Math.min(500, batchSize));
    this.sendTimeoutMs = Math.max(1, sendTimeoutMs);
  }

  @Scheduled(fixedDelayString = "${cloudticket.outbox.poll-ms:1000}")
  public void publishScheduled() {
    publishOnce();
  }

  /** Publishes one bounded batch. Failures are persisted for a later retry. */
  public int publishOnce() {
    List<Map<String, Object>> events = jdbc.query(
        "SELECT event_id,event_type,aggregate_type,aggregate_id,payload,trace_id,schema_version,occurred_at FROM order_outbox "
            + "WHERE published_at IS NULL ORDER BY occurred_at,event_id LIMIT ?",
        (rs, rowNum) -> {
          Map<String, Object> value = new java.util.LinkedHashMap<>();
          value.put("eventId", rs.getString("event_id"));
          value.put("eventType", rs.getString("event_type"));
          value.put("aggregateType", rs.getString("aggregate_type"));
          value.put("aggregateId", rs.getString("aggregate_id"));
          value.put("payload", rs.getString("payload"));
          value.put("traceId", rs.getString("trace_id"));
          value.put("schemaVersion", rs.getInt("schema_version"));
          value.put("occurredAt", rs.getTimestamp("occurred_at").toInstant().toString());
          return value;
        },
        batchSize);
    int published = 0;
    for (Map<String, Object> event : events) {
      String eventId = String.valueOf(event.get("eventId"));
      try {
        String envelope = envelope(event);
        var result = kafka.send(topic, String.valueOf(event.get("aggregateId")), envelope);
        if (result != null) result.get(sendTimeoutMs, TimeUnit.MILLISECONDS);
        jdbc.update("UPDATE order_outbox SET published_at=CURRENT_TIMESTAMP,last_error=NULL WHERE event_id=? AND published_at IS NULL", eventId);
        published++;
      } catch (Exception failure) {
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        try {
          jdbc.update("UPDATE order_outbox SET attempts = attempts + 1,last_error=? WHERE event_id=?", message, eventId);
        } catch (RuntimeException persistFailure) {
          log.warn("Unable to persist outbox failure for {}", eventId, persistFailure);
        }
        log.warn("Order outbox event {} could not be published", eventId, failure);
      }
    }
    return published;
  }

  private static String envelope(Map<String, Object> event) {
    String trace = event.get("traceId") == null ? "null" : "\"" + jsonEscape(String.valueOf(event.get("traceId"))) + "\"";
    String payload = String.valueOf(event.getOrDefault("payload", "null"));
    String occurredAt = jsonEscape(String.valueOf(event.getOrDefault("occurredAt", "")));
    return "{\"eventId\":\"" + jsonEscape(String.valueOf(event.get("eventId")))
        + "\",\"eventType\":\"" + jsonEscape(String.valueOf(event.getOrDefault("eventType", "OrderCreated")))
        + "\",\"aggregateType\":\"" + jsonEscape(String.valueOf(event.getOrDefault("aggregateType", "ORDER")))
        + "\",\"aggregateId\":\"" + jsonEscape(String.valueOf(event.get("aggregateId")))
        + "\",\"occurredAt\":\"" + occurredAt
        + "\",\"schemaVersion\":" + String.valueOf(event.getOrDefault("schemaVersion", 1))
        + ",\"traceId\":" + trace + ",\"payload\":" + payload + "}";
  }

  private static String jsonEscape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}

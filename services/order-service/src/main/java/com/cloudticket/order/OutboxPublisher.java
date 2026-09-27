package com.cloudticket.order;

import com.cloudticket.order.persistence.entity.OrderOutboxEntity;
import com.cloudticket.order.persistence.mapper.OrderOutboxMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Publishes durable order events without participating in the order write transaction. */
@Component
@ConditionalOnProperty(prefix = "cloudticket.outbox", name = "enabled", havingValue = "true")
public class OutboxPublisher {
  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

  private final OrderOutboxMapper outbox;
  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper json;
  private final String topic;
  private final int batchSize;
  private final long sendTimeoutMs;
  private final long leaseSeconds;

  @Autowired
  public OutboxPublisher(
      OrderOutboxMapper outbox,
      KafkaTemplate<String, String> kafka,
      ObjectMapper json,
      @Value("${cloudticket.outbox.topic:order-events}") String topic,
      @Value("${cloudticket.outbox.batch-size:50}") int batchSize,
      @Value("${cloudticket.outbox.send-timeout-ms:1000}") long sendTimeoutMs,
      @Value("${cloudticket.outbox.lease-seconds:30}") long leaseSeconds) {
    this.outbox = outbox;
    this.kafka = kafka;
    this.json = json;
    this.topic = topic;
    this.batchSize = Math.max(1, Math.min(500, batchSize));
    this.sendTimeoutMs = Math.max(1, sendTimeoutMs);
    long batchLease = (this.batchSize * this.sendTimeoutMs + 999L) / 1000L + 5L;
    this.leaseSeconds = Math.max(5, Math.max(leaseSeconds, Math.min(3600, batchLease)));
  }

  public OutboxPublisher(OrderOutboxMapper outbox, KafkaTemplate<String, String> kafka, ObjectMapper json,
                         String topic, int batchSize, long sendTimeoutMs) {
    this(outbox, kafka, json, topic, batchSize, sendTimeoutMs, 30);
  }

  @Scheduled(fixedDelayString = "${cloudticket.outbox.poll-ms:1000}")
  public void publishScheduled() {
    publishOnce();
  }

  /** Publishes one bounded batch. Failures are persisted for a later retry. */
  public int publishOnce() {
    String claimToken = UUID.randomUUID().toString();
    outbox.claimPending(claimToken, Instant.now().plusSeconds(leaseSeconds), batchSize);
    List<OrderOutboxEntity> events = outbox.selectClaimed(claimToken, batchSize);
    int published = 0;
    for (OrderOutboxEntity event : events) {
      String eventId = event.getEventId();
      try {
        String envelope = envelope(event);
        var result = kafka.send(topic, event.getAggregateId(), envelope);
        if (result != null) result.get(sendTimeoutMs, TimeUnit.MILLISECONDS);
        outbox.markPublished(eventId, claimToken);
        published++;
      } catch (Exception failure) {
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        try {
          outbox.recordFailure(eventId, message, claimToken);
        } catch (RuntimeException persistFailure) {
          log.warn("Unable to persist outbox failure for {}", eventId, persistFailure);
        }
        log.warn("Order outbox event {} could not be published", eventId, failure);
      }
    }
    return published;
  }

  /** Builds the Kafka envelope from the stored row, embedding the payload as real JSON. */
  private String envelope(OrderOutboxEntity event) {
    ObjectNode node = json.createObjectNode();
    node.put("eventId", event.getEventId());
    node.put("eventType", event.getEventType());
    node.put("aggregateType", event.getAggregateType());
    node.put("aggregateId", event.getAggregateId());
    node.put("occurredAt", event.getOccurredAt() == null ? null : event.getOccurredAt().toString());
    node.put("schemaVersion", event.getSchemaVersion() == null ? 1 : event.getSchemaVersion());
    if (event.getTraceId() == null) node.putNull("traceId");
    else node.put("traceId", event.getTraceId());
    node.set("payload", payloadNode(event.getPayload()));
    return node.toString();
  }

  private JsonNode payloadNode(String payload) {
    if (payload == null || payload.isBlank()) return json.nullNode();
    try {
      return json.readTree(payload);
    } catch (Exception unreadable) {
      log.warn("Outbox payload is not valid JSON, forwarding it as a string", unreadable);
      return json.getNodeFactory().textNode(payload);
    }
  }
}

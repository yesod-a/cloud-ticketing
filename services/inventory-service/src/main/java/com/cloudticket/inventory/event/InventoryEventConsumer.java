package com.cloudticket.inventory.event;

import com.cloudticket.common.events.EventTypes;
import com.cloudticket.inventory.persistence.ProcessedInventoryEventRepository;
import com.cloudticket.inventory.service.InventoryReservationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Idempotent order-event consumer that projects terminal order states into durable inventory. */
@Component
@ConditionalOnProperty(prefix = "cloudticket.inventory.events", name = "enabled", havingValue = "true")
public class InventoryEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);
  private static final String CONSUMER = "inventory-seat-projection-v1";

  private final InventoryReservationService reservations;
  private final ProcessedInventoryEventRepository events;
  private final ObjectMapper json;

  public InventoryEventConsumer(InventoryReservationService reservations,
                                ProcessedInventoryEventRepository events, ObjectMapper json) {
    this.reservations = reservations;
    this.events = events;
    this.json = json;
  }

  @KafkaListener(topics = "${cloudticket.inventory.events.topic:order-events}",
      groupId = "${cloudticket.inventory.events.group-id:inventory-seat-projection}")
  @Transactional
  public void consume(String raw) {
    JsonNode envelope;
    try {
      envelope = json.readTree(raw);
    } catch (Exception invalid) {
      log.warn("Ignoring malformed order event envelope");
      return;
    }
    String eventType = text(envelope, "eventType");
    String eventId = text(envelope, "eventId");
    if (!supported(eventType)) return;
    JsonNode payload = envelope.path("payload");
    String orderId = text(payload, "orderId");
    if (orderId == null) orderId = text(envelope, "aggregateId");
    if (eventId == null || orderId == null) {
      log.warn("Ignoring order event without eventId or orderId");
      return;
    }
    String traceId = text(envelope, "traceId");
    if (!events.tryClaim(eventId, eventType, orderId, traceId)) return;

    if (EventTypes.PAYMENT_SUCCEEDED.equals(eventType)) reservations.confirm(orderId);
    else reservations.release(orderId);
  }

  private static boolean supported(String eventType) {
    return EventTypes.PAYMENT_SUCCEEDED.equals(eventType)
        || EventTypes.ORDER_CANCELLED.equals(eventType)
        || EventTypes.ORDER_EXPIRED.equals(eventType)
        || EventTypes.ORDER_REFUNDED.equals(eventType);
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    if (value == null || value.isNull() || !value.isValueNode()) return null;
    String text = value.asText();
    return text == null || text.isBlank() ? null : text;
  }
}

package com.cloudticket.inventory.event;

import com.cloudticket.common.events.EventTypes;
import com.cloudticket.inventory.persistence.ProcessedInventoryEventRepository;
import com.cloudticket.inventory.persistence.InventoryReservationRepository;
import com.cloudticket.inventory.redis.QueuedAdmissionReservationService;
import com.cloudticket.inventory.redis.QueuedSeatReservationService;
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
  private final InventoryReservationRepository queuedRecords;
  private final QueuedSeatReservationService queuedSeats;
  private final QueuedAdmissionReservationService queuedAdmission;

  public InventoryEventConsumer(InventoryReservationService reservations,
                                ProcessedInventoryEventRepository events, ObjectMapper json) {
    this(reservations, events, json, null, null, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public InventoryEventConsumer(InventoryReservationService reservations,
                                ProcessedInventoryEventRepository events, ObjectMapper json,
                                InventoryReservationRepository queuedRecords,
                                QueuedSeatReservationService queuedSeats,
                                QueuedAdmissionReservationService queuedAdmission) {
    this.reservations = reservations;
    this.events = events;
    this.json = json;
    this.queuedRecords = queuedRecords;
    this.queuedSeats = queuedSeats;
    this.queuedAdmission = queuedAdmission;
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

    var queued = queuedRecords == null ? java.util.Optional.<com.cloudticket.inventory.persistence.entity.InventoryReservationEntity>empty()
        : queuedRecords.find(orderId);
    if (EventTypes.ORDER_CREATED.equals(eventType)) {
      reservations.promote(orderId);
    } else if (EventTypes.PAYMENT_SUCCEEDED.equals(eventType)) {
      reservations.confirm(orderId);
      queued.ifPresent(row -> confirmQueued(row));
    } else {
      reservations.release(orderId);
      queued.ifPresent(row -> releaseQueued(row));
    }
  }

  private void confirmQueued(com.cloudticket.inventory.persistence.entity.InventoryReservationEntity row) {
    if ("GENERAL_ADMISSION".equals(row.getMode())) queuedAdmission.confirm(row.getReservationId(), row.getSessionId(), row.getQuantity() == null ? 0 : row.getQuantity());
    else queuedSeats.confirm(row.getReservationId(), row.getSessionId(), parseIndexes(row.getSeatIndexes()));
  }

  private void releaseQueued(com.cloudticket.inventory.persistence.entity.InventoryReservationEntity row) {
    if ("GENERAL_ADMISSION".equals(row.getMode())) queuedAdmission.release(row.getReservationId(), row.getSessionId(), row.getQuantity() == null ? 0 : row.getQuantity());
    else queuedSeats.release(row.getReservationId(), row.getSessionId(), parseIndexes(row.getSeatIndexes()));
  }

  private static java.util.List<Integer> parseIndexes(String value) {
    if (value == null || value.isBlank()) return java.util.List.of();
    return java.util.Arrays.stream(value.split(",")).filter(v -> !v.isBlank()).map(Integer::parseInt).toList();
  }

  private static boolean supported(String eventType) {
    return EventTypes.PAYMENT_SUCCEEDED.equals(eventType)
        || EventTypes.ORDER_CREATED.equals(eventType)
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

package com.cloudticket.inventory.event;

import com.cloudticket.inventory.persistence.InventoryLockRepository;
import com.cloudticket.inventory.persistence.InventoryReservationRepository;
import com.cloudticket.inventory.persistence.InventorySeatRepository;
import com.cloudticket.inventory.persistence.InventoryOutboxWriter;
import com.cloudticket.inventory.persistence.entity.InventoryReservationEntity;
import com.cloudticket.common.events.EventEnvelope;
import com.cloudticket.common.events.EventTypes;
import com.cloudticket.inventory.service.AdmissionReservationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

/** Durable, at-least-once consumer for Redis-admitted reservations. */
@Component
@ConditionalOnProperty(prefix = "cloudticket.queued", name = "enabled", havingValue = "true")
public class QueuedReservationConsumer {
  private final InventoryReservationRepository records;
  private final InventorySeatRepository seats;
  private final InventoryLockRepository locks;
  private final AdmissionReservationService admission;
  private final ObjectMapper json;
  private final InventoryOutboxWriter outbox;
  private final String inventoryTopic;

  public QueuedReservationConsumer(InventoryReservationRepository records, InventorySeatRepository seats,
      InventoryLockRepository locks, AdmissionReservationService admission, ObjectMapper json) {
    this(records, seats, locks, admission, json, null, "inventory-events");
  }

  public QueuedReservationConsumer(InventoryReservationRepository records, InventorySeatRepository seats,
      InventoryLockRepository locks, AdmissionReservationService admission, ObjectMapper json,
      InventoryOutboxWriter outbox) {
    this(records, seats, locks, admission, json, outbox, "inventory-events");
  }

  @org.springframework.beans.factory.annotation.Autowired
  public QueuedReservationConsumer(InventoryReservationRepository records, InventorySeatRepository seats,
      InventoryLockRepository locks, AdmissionReservationService admission, ObjectMapper json,
      InventoryOutboxWriter outbox,
      @org.springframework.beans.factory.annotation.Value("${cloudticket.queued.inventory-topic:inventory-events}") String inventoryTopic) {
    this.records = records; this.seats = seats; this.locks = locks; this.admission = admission; this.json = json; this.outbox = outbox; this.inventoryTopic = inventoryTopic;
  }

  @KafkaListener(topics = "${cloudticket.queued.topic:ticket-reservations-v1}",
      groupId = "${cloudticket.queued.consumer-group:inventory-queued-v1}")
  @Transactional
  public void consume(String raw) {
    try {
      JsonNode node = json.readTree(raw);
      String reservationId = text(node, "reservationId");
      String sessionId = text(node, "sessionId");
      String userId = text(node, "userId");
      if (reservationId == null || sessionId == null || userId == null) throw new IllegalArgumentException("invalid reservation command");
      var prior = records.find(reservationId);
      if (prior.isPresent() && List.of("INVENTORY_HELD", "ORDER_CREATED", "PAID", "RELEASED").contains(prior.get().getStatus())) return;
      if (prior.isEmpty()) records.insertIfAbsent(record(node, reservationId, sessionId, userId));
      List<String> durableSeatIds = node.has("seatIndexes") ? persistSeats(node, reservationId, sessionId) : List.of();
      if (!durableSeatIds.isEmpty()) {
        records.updateSeatMapping(reservationId, String.join(",", durableSeatIds), text(node, "seatIndexes"));
      }
      if (!node.has("seatIndexes")) persistAdmission(node, reservationId, sessionId, userId);
      records.transition(reservationId, "PUBLISHED", "INVENTORY_HELD", null, null);
      if (outbox != null) {
        var event = new EventEnvelope<>(UUID.randomUUID().toString(), EventTypes.INVENTORY_HELD,
            "RESERVATION", reservationId, Instant.now(), 1, null,
            eventPayload(reservationId, sessionId, userId, node, durableSeatIds));
        outbox.write(inventoryTopic, reservationId, json.writeValueAsString(event));
      }
    } catch (Exception failure) {
      throw new IllegalStateException("queued reservation processing failed", failure);
    }
  }

  private List<String> persistSeats(JsonNode node, String reservationId, String sessionId) {
    List<Integer> indexes = Arrays.stream(text(node, "seatIndexes").split(","))
        .filter(v -> !v.isBlank()).map(Integer::parseInt).toList();
    List<String> ids = seats.idsByIndexes(sessionId, indexes);
    if (ids.size() != indexes.size()) throw new IllegalStateException("seat index no longer exists");
    if (seats.lock(sessionId, ids) != ids.size()) throw new IllegalStateException("seat no longer available");
    locks.holdBatch(reservationId, sessionId, ids, expiry(node));
    return ids;
  }

  private static java.util.Map<String, Object> eventPayload(String reservationId, String sessionId,
      String userId, JsonNode node, List<String> durableSeatIds) {
    java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
    payload.put("reservationId", reservationId); payload.put("sessionId", sessionId); payload.put("userId", userId);
    payload.put("seatIds", durableSeatIds == null || durableSeatIds.isEmpty() ? null : String.join(",", durableSeatIds));
    payload.put("ticketNumbers", text(node, "firstTicketNumber"));
    payload.put("quantity", intValue(node, "quantity")); return payload;
  }

  private void persistAdmission(JsonNode node, String reservationId, String sessionId, String userId) {
    int quantity = intValue(node, "quantity");
    long first = longValue(node, "firstTicketNumber");
    admission.persistQueued(reservationId, userId, sessionId, quantity, first, secondsUntil(expiry(node)));
  }

  private static InventoryReservationEntity record(JsonNode node, String id, String session, String user) {
    InventoryReservationEntity row = new InventoryReservationEntity(); row.setReservationId(id); row.setSessionId(session); row.setUserId(user);
    row.setMode(node.has("seatIndexes") ? "SEATED" : "GENERAL_ADMISSION"); row.setQuantity(intValue(node, "quantity"));
    row.setSeatIndexes(text(node, "seatIndexes")); row.setSeatIds(null); row.setTicketNumbers(text(node, "firstTicketNumber")); row.setStatus("PUBLISHED"); row.setExpiresAt(expiry(node)); row.setAttempts(0);
    return row;
  }
  private static Instant expiry(JsonNode node) { long millis = longValue(node, "expiresAt"); return millis > 0 ? Instant.ofEpochMilli(millis) : Instant.now().plusSeconds(900); }
  private static long secondsUntil(Instant at) { return Math.max(1, java.time.Duration.between(Instant.now(), at).toSeconds()); }
  private static String text(JsonNode n,String f){JsonNode v=n.get(f);return v==null||v.isNull()||v.asText().isBlank()?null:v.asText();}
  private static int intValue(JsonNode n,String f){return n.has(f)?n.get(f).asInt():0;}
  private static long longValue(JsonNode n,String f){return n.has(f)?n.get(f).asLong():0L;}
}

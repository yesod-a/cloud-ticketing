package com.cloudticket.inventory.event;

import com.cloudticket.inventory.redis.QueuedSeatReservationService;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Publishes Redis-admitted reservations with a lease so crashes are replayable. */
@Component
public class QueuedReservationPublisher {
  private final QueuedSeatReservationService reservations;
  private final com.cloudticket.inventory.redis.QueuedAdmissionReservationService admissions;
  private final KafkaTemplate<String, String> kafka;
  private final com.fasterxml.jackson.databind.ObjectMapper json;
  private final String topic;
  private final String sessionId;
  private final boolean enabled;

  public QueuedReservationPublisher(QueuedSeatReservationService reservations,
      com.cloudticket.inventory.redis.QueuedAdmissionReservationService admissions,
      KafkaTemplate<String, String> kafka, com.fasterxml.jackson.databind.ObjectMapper json,
      @Value("${cloudticket.queued.topic:ticket-reservations-v1}") String topic,
      @Value("${cloudticket.queued.publisher-session:}") String sessionId,
      @Value("${cloudticket.queued.publisher-enabled:false}") boolean enabled) {
    this.reservations = reservations; this.admissions = admissions; this.kafka = kafka; this.json = json;
    this.topic = topic; this.sessionId = sessionId; this.enabled = enabled;
  }

  @Scheduled(fixedDelayString = "${cloudticket.queued.publisher-poll-ms:200}")
  public void publishOnce() {
    if (!enabled || !reservations.isEnabled()) return;
    long now = System.currentTimeMillis();
    Set<String> sessions = new HashSet<>(reservations.sessions());
    sessions.addAll(admissions.sessions());
    if (sessionId != null && !sessionId.isBlank()) sessions.add(sessionId);
    for (String currentSession : sessions) {
      for (String id : reservations.due(currentSession, now, 100)) {
        if (!reservations.claim(id, currentSession, now + 30_000)) continue;
        try {
          Map<Object, Object> record = reservations.reservation(id, currentSession);
          if (record.isEmpty()) record = admissions.reservation(id, currentSession);
          Map<String, Object> payload = new java.util.LinkedHashMap<>();
          payload.put("reservationId", id); payload.put("sessionId", currentSession);
          payload.put("eventType", "ReserveTicketCommand"); payload.put("schemaVersion", 1);
          record.forEach((key, value) -> payload.put(String.valueOf(key), value));
          String body = json.writeValueAsString(payload);
          kafka.send(topic, currentSession, body).whenComplete((ignored, failure) -> {
            if (failure == null) reservations.published(id, currentSession);
            else reservations.requeue(id, currentSession, System.currentTimeMillis() + 1000);
          });
        } catch (Exception failure) {
          reservations.requeue(id, currentSession, now + 1000);
        }
      }
    }
  }
}

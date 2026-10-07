package com.cloudticket.inventory.reconcile;

import com.cloudticket.inventory.persistence.InventoryReconciliationDiffRepository;
import com.cloudticket.inventory.persistence.InventoryReservationRepository;
import com.cloudticket.inventory.persistence.InventorySeatRepository;
import com.cloudticket.inventory.persistence.entity.InventoryReservationEntity;
import com.cloudticket.inventory.redis.QueuedAdmissionReservationService;
import com.cloudticket.inventory.redis.QueuedSeatReservationService;
import com.cloudticket.inventory.service.AdmissionReservationService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Repairs the small windows where Redis admission and Kafka/MySQL processing can diverge. */
@Component
@ConditionalOnProperty(prefix = "cloudticket.queued", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "cloudticket.inventory.reconciliation", name = "enabled", havingValue = "true")
public class QueuedReservationReconciliationScheduler {
  private final QueuedSeatReservationService seats;
  private final QueuedAdmissionReservationService admission;
  private final InventoryReservationRepository records;
  private final InventorySeatRepository durableSeats;
  private final AdmissionReservationService durableAdmission;
  private final InventoryReconciliationDiffRepository diffs;

  public QueuedReservationReconciliationScheduler(QueuedSeatReservationService seats,
      QueuedAdmissionReservationService admission, InventoryReservationRepository records,
      InventorySeatRepository durableSeats, AdmissionReservationService durableAdmission,
      InventoryReconciliationDiffRepository diffs) {
    this.seats = seats; this.admission = admission; this.records = records;
    this.durableSeats = durableSeats; this.durableAdmission = durableAdmission; this.diffs = diffs;
  }

  @Scheduled(fixedDelayString = "${cloudticket.inventory.reconciliation.poll-ms:60000}")
  @Transactional
  public void reconcile() {
    long now = System.currentTimeMillis();
    Set<String> sessions = new HashSet<>(seats.sessions());
    sessions.addAll(admission.sessions());
    for (String sessionId : sessions) {
      for (String id : seats.inflightDue(sessionId, now, 100)) {
        seats.requeue(id, sessionId, now);
      }
      Set<String> candidates = new HashSet<>(seats.due(sessionId, now, 100));
      candidates.addAll(seats.inflightDue(sessionId, now, 100));
      for (String id : candidates) expireRedisAdmission(id, sessionId, now);
    }
    for (InventoryReservationEntity row : records.expiredHeld(Instant.ofEpochMilli(now), 100)) {
      expireDurableHeld(row);
    }
  }

  private void expireRedisAdmission(String id, String sessionId, long now) {
    Map<Object, Object> row = seats.reservation(id, sessionId);
    boolean seated = !row.isEmpty() && row.containsKey("seatIndexes");
    if (row.isEmpty()) row = admission.reservation(id, sessionId);
    if (row.isEmpty() || !isExpired(row, now)) return;
    String status = value(row, "status");
    if ("SOLD".equals(status) || "RELEASED".equals(status) || "FAILED".equals(status)) return;
    boolean repaired = seated
        ? seats.release(id, sessionId, parseIndexes(value(row, "seatIndexes")))
        : admission.release(id, sessionId, intValue(row, "quantity"));
    if (repaired) {
      records.transition(id, "PUBLISHED", "RELEASED", "redis reservation expired before durable hold", null);
      diffs.open(id, sessionId, "REDIS_EXPIRED_BEFORE_MQ", status, "RELEASED", "reservation TTL elapsed");
    }
  }

  private void expireDurableHeld(InventoryReservationEntity row) {
    if ("GENERAL_ADMISSION".equals(row.getMode())) {
      admission.release(row.getReservationId(), row.getSessionId(), row.getQuantity() == null ? 0 : row.getQuantity());
      var count = row.getQuantity() == null ? 0 : row.getQuantity();
      durableAdmission.release(row.getReservationId());
    } else {
      durableSeats.release(split(row.getSeatIds()));
      seats.release(row.getReservationId(), row.getSessionId(), parseIndexes(row.getSeatIndexes()));
    }
    if (records.transition(row.getReservationId(), "INVENTORY_HELD", "RELEASED", "durable reservation expired", null) > 0) {
      diffs.open(row.getReservationId(), row.getSessionId(), "DURABLE_HELD_EXPIRED", "INVENTORY_HELD", "RELEASED", "reconciliation TTL repair");
    }
  }

  private static boolean isExpired(Map<Object, Object> row, long now) {
    try { return Long.parseLong(value(row, "expiresAt")) <= now; } catch (RuntimeException ignored) { return false; }
  }
  private static String value(Map<Object, Object> row, String key) { Object v = row.get(key); return v == null ? "" : String.valueOf(v); }
  private static int intValue(Map<Object, Object> row, String key) { try { return Integer.parseInt(value(row, key)); } catch (RuntimeException ignored) { return 0; } }
  private static List<Integer> parseIndexes(String value) { List<Integer> result = new ArrayList<>(); for (String item : value.split(",")) try { if (!item.isBlank()) result.add(Integer.parseInt(item)); } catch (NumberFormatException ignored) {} return result; }
  private static List<String> split(String value) { if (value == null || value.isBlank()) return List.of(); return java.util.Arrays.stream(value.split(",")).filter(v -> !v.isBlank()).toList(); }
}

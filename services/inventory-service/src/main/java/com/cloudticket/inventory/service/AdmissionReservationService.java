package com.cloudticket.inventory.service;

import com.cloudticket.inventory.persistence.AdmissionInventoryRepository;
import com.cloudticket.inventory.persistence.AdmissionTicketRepository;
import com.cloudticket.inventory.persistence.entity.AdmissionInventoryEntity;
import com.cloudticket.inventory.persistence.entity.AdmissionTicketEntity;
import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Row-lock based capacity reservation and monotonic numbered-ticket allocation. */
@Service
public class AdmissionReservationService {
  private static final long MAX_TTL_SECONDS = 1800;
  private final AdmissionInventoryRepository inventory;
  private final AdmissionTicketRepository tickets;

  public AdmissionReservationService(AdmissionInventoryRepository inventory, AdmissionTicketRepository tickets) {
    this.inventory = inventory;
    this.tickets = tickets;
  }

  @Transactional
  public AdmissionReservation reserveQuantity(String orderId, String userId, String sessionId, int quantity,
                                               long ttlSeconds) {
    require(orderId, "orderId");
    require(userId, "userId");
    require(sessionId, "sessionId");
    if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
    List<AdmissionTicketEntity> existing = tickets.findHeldByOrder(orderId);
    if (existing != null && !existing.isEmpty()) {
      return reservation(orderId, userId, sessionId, existing);
    }
    AdmissionInventoryEntity row = inventory.lock(sessionId);
    int capacity = value(row.getCapacity());
    int reserved = value(row.getReservedCount());
    int sold = value(row.getSoldCount());
    if ((long) reserved + sold + quantity > capacity) throw new CapacityExceededException();
    long first = row.getNextTicketNumber() == null ? 1L : row.getNextTicketNumber();
    List<Long> numbers = LongStream.range(first, first + quantity).boxed().toList();
    long next = first + quantity;
    long version = row.getVersion() == null ? 0L : row.getVersion();
    inventory.save(sessionId, capacity, reserved + quantity, sold, next, version + 1);
    long ttl = Math.max(1, Math.min(ttlSeconds, MAX_TTL_SECONDS));
    Instant expiresAt = Instant.now().plusSeconds(ttl);
    tickets.insertHeld(orderId, userId, sessionId, numbers, expiresAt);
    return new AdmissionReservation(orderId, userId, sessionId, quantity, numbers, expiresAt);
  }

  /** Persists the ticket range already allocated by Redis without allocating a second range. */
  @Transactional
  public AdmissionReservation persistQueued(String reservationId, String userId, String sessionId,
                                             int quantity, long firstTicketNumber, long ttlSeconds) {
    require(reservationId, "reservationId"); require(userId, "userId"); require(sessionId, "sessionId");
    if (quantity <= 0 || firstTicketNumber < 1) throw new IllegalArgumentException("invalid queued allocation");
    List<AdmissionTicketEntity> existing = tickets.findHeldByOrder(reservationId);
    if (existing != null && !existing.isEmpty()) return reservation(reservationId, userId, sessionId, existing);
    AdmissionInventoryEntity row = inventory.lock(sessionId);
    int capacity = value(row.getCapacity()); int reserved = value(row.getReservedCount()); int sold = value(row.getSoldCount());
    long next = row.getNextTicketNumber() == null ? 1L : row.getNextTicketNumber();
    if ((long) reserved + sold + quantity > capacity || next > firstTicketNumber) throw new CapacityExceededException();
    long end = firstTicketNumber + quantity;
    inventory.save(sessionId, capacity, reserved + quantity, sold, Math.max(next, end), valueLong(row.getVersion()) + 1);
    Instant expiresAt = Instant.now().plusSeconds(Math.max(1, Math.min(ttlSeconds, MAX_TTL_SECONDS)));
    tickets.insertHeld(reservationId, userId, sessionId,
        LongStream.range(firstTicketNumber, end).boxed().toList(), expiresAt);
    return new AdmissionReservation(reservationId, userId, sessionId, quantity,
        LongStream.range(firstTicketNumber, end).boxed().toList(), expiresAt);
  }

  @Transactional
  public int confirm(String orderId) {
    if (orderId == null || orderId.isBlank()) return 0;
    List<AdmissionTicketEntity> held = tickets.findHeldByOrder(orderId);
    if (held == null || held.isEmpty()) return 0;
    int count = tickets.markSold(orderId);
    if (count <= 0) return 0;
    inventory.moveReservedToSold(held.get(0).getSessionId(), count);
    return count;
  }

  @Transactional
  public int release(String orderId) {
    if (orderId == null || orderId.isBlank()) return 0;
    List<AdmissionTicketEntity> held = tickets.findHeldByOrder(orderId);
    if (held != null && !held.isEmpty()) {
      int count = tickets.markReleased(orderId);
      if (count <= 0) return 0;
      inventory.releaseReserved(held.get(0).getSessionId(), count);
      return count;
    }
    return releaseRefunded(orderId);
  }

  @Transactional
  public int releaseRefunded(String orderId) {
    if (orderId == null || orderId.isBlank()) return 0;
    List<AdmissionTicketEntity> active = tickets.findActiveByOrder(orderId);
    if (active == null || active.isEmpty()) return 0;
    int count = tickets.markReleasedActive(orderId);
    if (count <= 0) return 0;
    long held = active.stream().filter(t -> "HELD".equals(t.getState())).count();
    if (held > 0) inventory.releaseReserved(active.get(0).getSessionId(), (int) held);
    long sold = active.stream().filter(t -> "SOLD".equals(t.getState())).count();
    if (sold > 0) inventory.releaseSold(active.get(0).getSessionId(), (int) sold);
    return count;
  }

  public int remaining(String sessionId) {
    AdmissionInventoryEntity row = inventory.find(sessionId);
    if (row == null) return 0;
    return Math.max(0, value(row.getCapacity()) - value(row.getReservedCount()) - value(row.getSoldCount()));
  }

  @Scheduled(fixedDelayString = "${cloudticket.inventory-expiry-scan-ms:30000}")
  @Transactional
  public void expireReservations() {
    tickets.expiredOrderIds().forEach(this::release);
  }

  private static AdmissionReservation reservation(String orderId, String userId, String sessionId,
                                                  List<AdmissionTicketEntity> rows) {
    List<Long> numbers = rows.stream().map(AdmissionTicketEntity::getTicketNumber).toList();
    Instant expiry = rows.stream().map(AdmissionTicketEntity::getExpiresAt).filter(java.util.Objects::nonNull)
        .min(Instant::compareTo).orElse(Instant.now());
    return new AdmissionReservation(orderId, userId, sessionId, numbers.size(), numbers, expiry);
  }

  private static int value(Integer value) { return value == null ? 0 : value; }
  private static long valueLong(Long value) { return value == null ? 0L : value; }
  private static void require(String value, String field) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " required");
  }

  public record AdmissionReservation(String orderId, String userId, String sessionId, int quantity,
                                     List<Long> ticketNumbers, Instant expiresAt) {}

  @ResponseStatus(HttpStatus.CONFLICT)
  public static final class CapacityExceededException extends RuntimeException {
    public CapacityExceededException() { super("general admission capacity exceeded"); }
  }
}

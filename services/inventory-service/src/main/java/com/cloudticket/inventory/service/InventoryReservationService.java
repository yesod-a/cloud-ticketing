package com.cloudticket.inventory.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@Service
public class InventoryReservationService {
  private final JdbcTemplate jdbc;

  public InventoryReservationService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public static List<String> normalizeSeatIds(List<String> seatIds) {
    if (seatIds == null || seatIds.isEmpty()) throw new IllegalArgumentException("seatIds required");
    List<String> clean = seatIds.stream().map(value -> value == null ? "" : value.trim()).filter(value -> !value.isBlank()).toList();
    if (clean.isEmpty() || clean.size() > 6) throw new IllegalArgumentException("seatIds must contain 1-6 values");
    if (new LinkedHashSet<>(clean).size() != clean.size()) throw new IllegalArgumentException("duplicate seat id");
    return List.copyOf(clean);
  }

  @Transactional
  public Reservation reserve(String orderId, String sessionId, List<String> requestedSeatIds, long ttlSeconds) {
    if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("orderId required");
    if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("sessionId required");
    List<String> seatIds = normalizeSeatIds(requestedSeatIds);
    List<String> existing = jdbc.query("SELECT seat_id FROM inventory_lock WHERE order_id=? AND active=1", (r, n) -> r.getString(1), orderId);
    if (!existing.isEmpty()) return new Reservation(orderId, sessionId, existing, Instant.now().plusSeconds(Math.max(1, ttlSeconds)));
    String placeholders = String.join(",", java.util.Collections.nCopies(seatIds.size(), "?"));
    List<Object> args = new ArrayList<>();
    args.add(sessionId);
    args.addAll(seatIds);
    int changed = jdbc.update("UPDATE inventory_seat SET status='LOCKED' WHERE session_id=? AND id IN (" + placeholders + ") AND status='AVAILABLE'", args.toArray());
    if (changed != seatIds.size()) throw new SeatsUnavailableException();
    long ttl = Math.max(1, Math.min(ttlSeconds, 1800));
    Instant expiresAt = Instant.now().plusSeconds(ttl);
    for (String seatId : seatIds) {
      jdbc.update("INSERT INTO inventory_lock(id,order_id,session_id,seat_id,active,status,expires_at) VALUES(?,?,?,?,1,'ACTIVE',?)", UUID.randomUUID().toString(), orderId, sessionId, seatId, java.sql.Timestamp.from(expiresAt));
    }
    return new Reservation(orderId, sessionId, seatIds, expiresAt);
  }

  @Transactional
  public void release(String orderId) {
    if (orderId == null || orderId.isBlank()) return;
    List<String> seats = jdbc.query("SELECT seat_id FROM inventory_lock WHERE order_id=? AND active=1", (r, n) -> r.getString(1), orderId);
    jdbc.update("UPDATE inventory_lock SET active=0,status='RELEASED' WHERE order_id=? AND active=1", orderId);
    if (!seats.isEmpty()) {
      String placeholders = String.join(",", java.util.Collections.nCopies(seats.size(), "?"));
      List<Object> args = new ArrayList<>(seats);
      jdbc.update("UPDATE inventory_seat SET status='AVAILABLE' WHERE id IN (" + placeholders + ") AND status='LOCKED'", args.toArray());
    }
  }

  /** Converts a paid order's active locks into permanently sold seats. */
  @Transactional
  public int confirm(String orderId) {
    if (orderId == null || orderId.isBlank()) return 0;
    List<String> seats = jdbc.query("SELECT seat_id FROM inventory_lock WHERE order_id=? AND active=1", (r, n) -> r.getString(1), orderId);
    if (seats.isEmpty()) return 0;
    String placeholders = String.join(",", java.util.Collections.nCopies(seats.size(), "?"));
    List<Object> args = new ArrayList<>(seats);
    int sold = jdbc.update("UPDATE inventory_seat SET status='SOLD' WHERE id IN (" + placeholders + ") AND status='LOCKED'", args.toArray());
    jdbc.update("UPDATE inventory_lock SET active=0,status='CONFIRMED' WHERE order_id=? AND active=1", orderId);
    return sold;
  }

  @Scheduled(fixedDelayString = "${cloudticket.inventory-expiry-scan-ms:30000}")
  @Transactional
  public void expireReservations() {
    List<String> expired = jdbc.query("SELECT DISTINCT order_id FROM inventory_lock WHERE active=1 AND expires_at<=CURRENT_TIMESTAMP", (r, n) -> r.getString(1));
    expired.forEach(this::release);
  }

  public record Reservation(String orderId, String sessionId, List<String> seatIds, Instant expiresAt) {}
  @ResponseStatus(HttpStatus.CONFLICT)
  public static final class SeatsUnavailableException extends RuntimeException {
    public SeatsUnavailableException() { super("one or more seats are unavailable"); }
  }
}

package com.cloudticket.inventory.api;

import com.cloudticket.inventory.service.InventoryReservationService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/inventory/locks")
public class InternalInventoryLockController {
  private final InventoryReservationService reservations;
  private final String internalToken;

  public InternalInventoryLockController(InventoryReservationService reservations, @Value("${cloudticket.internal-service-token:dev-internal-token}") String internalToken) {
    this.reservations = reservations;
    this.internalToken = internalToken;
  }

  @PostMapping
  public Map<String, Object> reserve(@RequestHeader(value = "X-Internal-Service-Token", defaultValue = "") String token, @RequestBody Map<String, Object> body) {
    trusted(token);
    String orderId = String.valueOf(body.getOrDefault("orderId", ""));
    String sessionId = String.valueOf(body.getOrDefault("sessionId", ""));
    Object rawSeats = body.get("seatIds");
    List<String> seatIds = rawSeats instanceof List<?> values ? values.stream().map(String::valueOf).toList() : Arrays.stream(String.valueOf(rawSeats == null ? "" : rawSeats).split(",")).toList();
    long ttl = body.get("ttlSeconds") instanceof Number number ? number.longValue() : 900;
    var reservation = reservations.reserve(orderId, sessionId, seatIds, ttl);
    return Map.of("orderId", reservation.orderId(), "sessionId", reservation.sessionId(), "seatIds", reservation.seatIds(), "expiresAt", reservation.expiresAt().toString());
  }

  @PostMapping("/{orderId}/release")
  public Map<String, Object> release(@RequestHeader(value = "X-Internal-Service-Token", defaultValue = "") String token, @PathVariable String orderId) {
    trusted(token);
    reservations.release(orderId);
    return Map.of("orderId", orderId, "status", "RELEASED");
  }

  @PostMapping("/{orderId}/confirm")
  public Map<String, Object> confirm(@RequestHeader(value = "X-Internal-Service-Token", defaultValue = "") String token, @PathVariable String orderId) {
    trusted(token);
    int sold = reservations.confirm(orderId);
    return Map.of("orderId", orderId, "status", "CONFIRMED", "soldSeats", sold);
  }

  private void trusted(String token) { if (token == null || !token.equals(internalToken)) throw new SecurityException("internal authentication required"); }
}

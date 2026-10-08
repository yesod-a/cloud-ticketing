package com.cloudticket.inventory.api;

import com.cloudticket.common.security.RequireInternalToken;
import com.cloudticket.inventory.service.InventoryReservationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lock protocol used by order-service while an order is being created, paid or cancelled. */
@RestController
@RequireInternalToken
@RequestMapping("/api/internal/inventory/locks")
public class InternalInventoryLockController {

  private final InventoryReservationService reservations;

  public InternalInventoryLockController(InventoryReservationService reservations) {
    this.reservations = reservations;
  }

  @PostMapping
  public Map<String, Object> reserve(@RequestBody InventoryCommands.ReserveLocks body) {
    var reservation = reservations.reserve(body.orderId(), body.sessionId(), body.seatIdsAsList(),
        body.ttlOrDefault());
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("orderId", reservation.orderId());
    response.put("sessionId", reservation.sessionId());
    response.put("seatIds", reservation.seatIds());
    response.put("expiresAt", reservation.expiresAt().toString());
    return response;
  }

  @PostMapping("/{orderId}/release")
  public Map<String, Object> release(@PathVariable("orderId") String orderId) {
    reservations.release(orderId);
    return Map.of("orderId", orderId, "status", "RELEASED");
  }

  @PostMapping("/{orderId}/promote")
  public Map<String, Object> promote(@PathVariable("orderId") String orderId) {
    reservations.promote(orderId);
    return Map.of("orderId", orderId, "status", "ACTIVE");
  }

  @PostMapping("/{orderId}/confirm")
  public Map<String, Object> confirm(@PathVariable("orderId") String orderId) {
    int sold = reservations.confirm(orderId);
    return Map.of("orderId", orderId, "status", "CONFIRMED", "soldSeats", sold);
  }
}

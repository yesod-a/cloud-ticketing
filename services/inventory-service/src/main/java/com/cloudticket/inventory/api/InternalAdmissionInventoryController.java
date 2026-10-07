package com.cloudticket.inventory.api;

import com.cloudticket.common.security.RequireInternalToken;
import com.cloudticket.inventory.persistence.AdmissionInventoryRepository;
import com.cloudticket.inventory.persistence.entity.AdmissionInventoryEntity;
import com.cloudticket.inventory.service.AdmissionReservationService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal saga boundary for quantity-based general-admission reservations. */
@RestController
@RequireInternalToken
@RequestMapping("/api/internal/inventory/admission")
public class InternalAdmissionInventoryController {
  private final AdmissionReservationService reservations;
  private final AdmissionInventoryRepository inventory;

  public InternalAdmissionInventoryController(AdmissionReservationService reservations,
                                              AdmissionInventoryRepository inventory) {
    this.reservations = reservations;
    this.inventory = inventory;
  }

  @PostMapping("/ensure")
  public Map<String, Object> ensure(@RequestBody InventoryCommands.EnsureAdmission body) {
    inventory.ensure(body.sessionId(), body.capacityOrZero());
    return summary(body.sessionId());
  }

  @PostMapping("/reservations")
  public Map<String, Object> reserve(@RequestBody InventoryCommands.ReserveAdmission body) {
    var result = reservations.reserveQuantity(body.orderId(), body.userId(), body.sessionId(),
        body.quantityOrZero(), body.ttlOrDefault());
    return Map.of("orderId", result.orderId(), "userId", result.userId(), "sessionId", result.sessionId(),
        "quantity", result.quantity(), "ticketNumbers", result.ticketNumbers(),
        "expiresAt", result.expiresAt().toString());
  }

  @PostMapping("/reservations/{orderId}/confirm")
  public Map<String, Object> confirm(@PathVariable String orderId) {
    return Map.of("orderId", orderId, "state", "SOLD", "count", reservations.confirm(orderId));
  }

  @PostMapping("/reservations/{orderId}/release")
  public Map<String, Object> release(@PathVariable String orderId) {
    return Map.of("orderId", orderId, "state", "RELEASED", "count", reservations.release(orderId));
  }

  @PostMapping("/reservations/{orderId}/refund-release")
  public Map<String, Object> refundRelease(@PathVariable String orderId) {
    return Map.of("orderId", orderId, "state", "RELEASED", "count", reservations.releaseRefunded(orderId));
  }

  @GetMapping("/sessions/{sessionId}")
  public Map<String, Object> session(@PathVariable String sessionId) { return summary(sessionId); }

  private Map<String, Object> summary(String sessionId) {
    AdmissionInventoryEntity row = inventory.find(sessionId);
    if (row == null) throw new IllegalArgumentException("admission inventory not found");
    int capacity = value(row.getCapacity());
    int reserved = value(row.getReservedCount());
    int sold = value(row.getSoldCount());
    return Map.of("sessionId", sessionId, "capacity", capacity, "reservedCount", reserved,
        "soldCount", sold, "remainingCapacity", Math.max(0, capacity - reserved - sold),
        "nextTicketNumber", row.getNextTicketNumber() == null ? 1L : row.getNextTicketNumber());
  }

  private static int value(Integer value) { return value == null ? 0 : value; }
}

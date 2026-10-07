package com.cloudticket.order.api;

import com.cloudticket.order.client.ActivitySessionClient;
import com.cloudticket.order.client.InventoryReservationClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Hot-session entry point. It intentionally does not write MySQL on admission. */
@RestController
@RequestMapping("/api/queued-orders")
public class QueuedOrderController {
  private final ActivitySessionClient sessions;
  private final InventoryReservationClient inventory;

  public QueuedOrderController(ActivitySessionClient sessions, InventoryReservationClient inventory) {
    this.sessions = sessions; this.inventory = inventory;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, Object> enqueue(@RequestHeader("X-User-Id") String user,
                                     @RequestBody Map<String, Object> body) {
    String sessionId = text(body, "sessionId");
    String idempotencyKey = text(body, "idempotencyKey");
    ActivitySessionClient.SessionInfo session = sessions.session(sessionId);
    if (!"ONSALE".equalsIgnoreCase(session.status())) throw new IllegalStateException("session is not on sale");
    String mode = session.layoutMode();
    List<String> seatIds = split(text(body, "seatIds"));
    int quantity = body.get("quantity") instanceof Number n ? n.intValue() : 0;
    if ("GENERAL_ADMISSION".equalsIgnoreCase(mode) && quantity <= 0) throw new IllegalArgumentException("quantity required");
    if (!"GENERAL_ADMISSION".equalsIgnoreCase(mode) && seatIds.isEmpty()) throw new IllegalArgumentException("seatIds required");
    String reservationId = UUID.randomUUID().toString();
    Map<String, Object> response = inventory.reserveQueued(reservationId, user, sessionId, mode, seatIds, quantity,
        session.capacity(), idempotencyKey);
    response = new java.util.LinkedHashMap<>(response);
    response.putIfAbsent("id", response.get("reservationId"));
    return response;
  }

  @GetMapping("/{reservationId}")
  public Map<String, Object> status(@RequestHeader("X-User-Id") String user,
                                    @PathVariable String reservationId,
                                    @RequestParam String sessionId) {
    Map<String, Object> status = inventory.queuedStatus(reservationId, sessionId);
    Object owner = status.get("userId");
    if (owner != null && !user.equals(String.valueOf(owner))) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);
    return status;
  }

  private static String text(Map<String, Object> body, String key) {
    Object value = body == null ? null : body.get(key);
    return value == null ? "" : String.valueOf(value).trim();
  }
  private static List<String> split(String raw) {
    return java.util.Arrays.stream(raw.split(",")).map(String::trim).filter(v -> !v.isBlank()).toList();
  }
}

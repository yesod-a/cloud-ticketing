package com.cloudticket.inventory.api;

import com.cloudticket.common.security.RequireInternalToken;
import com.cloudticket.inventory.persistence.InventorySeatRepository;
import com.cloudticket.inventory.persistence.AdmissionInventoryRepository;
import com.cloudticket.inventory.redis.QueuedAdmissionReservationService;
import com.cloudticket.inventory.redis.QueuedSeatReservationService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequireInternalToken
@RequestMapping("/api/internal/inventory/queued")
public class InternalQueuedReservationController {
  private final InventorySeatRepository seats;
  private final QueuedSeatReservationService seated;
  private final QueuedAdmissionReservationService admission;
  private final AdmissionInventoryRepository admissionInventory;

  public InternalQueuedReservationController(InventorySeatRepository seats,
      QueuedSeatReservationService seated, QueuedAdmissionReservationService admission) {
    this(seats, seated, admission, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public InternalQueuedReservationController(InventorySeatRepository seats,
      QueuedSeatReservationService seated, QueuedAdmissionReservationService admission,
      AdmissionInventoryRepository admissionInventory) {
    this.seats = seats; this.seated = seated; this.admission = admission; this.admissionInventory = admissionInventory;
  }

  @PostMapping("/reservations")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, Object> reserve(@RequestBody QueuedReservationCommands.Reserve body) {
    String reservationId = body.reservationId() == null || body.reservationId().isBlank()
        ? UUID.randomUUID().toString() : body.reservationId();
    String idempotency = body.idempotencyKey() == null || body.idempotencyKey().isBlank()
        ? reservationId : body.idempotencyKey();
    if ("GENERAL_ADMISSION".equals(body.modeOrDefault())) {
      if (body.capacity() == null || body.capacity() <= 0) throw new IllegalArgumentException("capacity required");
      var durable = admissionInventory == null ? null : admissionInventory.find(body.sessionId());
      int reserved = durable == null || durable.getReservedCount() == null ? 0 : durable.getReservedCount();
      int sold = durable == null || durable.getSoldCount() == null ? 0 : durable.getSoldCount();
      long next = durable == null || durable.getNextTicketNumber() == null ? 1L : durable.getNextTicketNumber();
      admission.initialize(body.sessionId(), body.capacity(), next,
          Math.max(0, body.capacity() - reserved - sold), reserved, sold);
      var result = admission.reserve(reservationId, body.userId(), body.sessionId(), body.quantityOrZero(),
          body.ttlOrDefault(), idempotency);
      return Map.of("reservationId", result.reservationId(), "status", result.accepted() ? "QUEUED" : "SOLD_OUT",
          "duplicate", result.duplicate(), "firstTicketNumber", result.firstTicketNumber());
    }
    List<String> requested = body.seatIds() == null ? List.of() : body.seatIds();
    var indexes = seats.indexes(body.sessionId(), requested);
    if (indexes.size() != requested.size()) throw new IllegalArgumentException("unknown seat");
    var result = seated.reserve(reservationId, body.userId(), body.sessionId(), requested.stream().map(indexes::get).toList(),
        body.ttlOrDefault(), idempotency);
    return Map.of("reservationId", result.reservationId(), "status", result.accepted() ? "QUEUED" : "SOLD_OUT",
        "duplicate", result.duplicate());
  }

  @GetMapping("/reservations/{reservationId}")
  public Map<String, Object> status(@PathVariable String reservationId,
                                    @RequestParam String sessionId) {
    Map<Object, Object> record = seated.reservation(reservationId, sessionId);
    if (record.isEmpty()) record = admission.reservation(reservationId, sessionId);
    if (record.isEmpty()) return Map.of("reservationId", reservationId, "status", "UNKNOWN");
    Map<String, Object> response = new java.util.LinkedHashMap<>();
    response.put("reservationId", reservationId);
    record.forEach((key, value) -> response.put(String.valueOf(key), value));
    return response;
  }
}

package com.cloudticket.inventory.api;

import com.cloudticket.common.web.ApiResponse;
import com.cloudticket.inventory.cache.InventoryReadService;
import com.cloudticket.inventory.cache.SeatBitmapProjection;
import com.cloudticket.inventory.persistence.InventorySeatRepository;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Anonymous seat map of a session, owned by the inventory service. */
@RestController
@RequestMapping("/api/sessions")
public class PublicSeatController {

  private final InventorySeatRepository seats;
  private final InventoryReadService reads;
  private final SeatBitmapProjection legacyProjection;

  public PublicSeatController(InventorySeatRepository seats) {
    this(seats, null, null);
  }

  public PublicSeatController(InventorySeatRepository seats, SeatBitmapProjection projection) {
    this(seats, null, projection);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public PublicSeatController(InventorySeatRepository seats, InventoryReadService reads) {
    this(seats, reads, null);
  }

  private PublicSeatController(InventorySeatRepository seats, InventoryReadService reads,
                               SeatBitmapProjection legacyProjection) {
    this.seats = seats;
    this.reads = reads;
    this.legacyProjection = legacyProjection;
  }

  @GetMapping("/{sessionId}/seats")
  public Map<String, Object> seats(@PathVariable("sessionId") String sessionId) {
    var rows = reads == null ? seats.listBySession(sessionId) : reads.seats(sessionId);
    if (reads == null && legacyProjection != null) rows = legacyProjection.overlay(sessionId, rows);
    return ApiResponse.ok("ok", rows.stream()
        .map(InventoryViews::publicSeat)
        .toList());
  }
}

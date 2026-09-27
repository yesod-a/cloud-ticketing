package com.cloudticket.inventory.api;

import com.cloudticket.common.web.ApiResponse;
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
  private final SeatBitmapProjection projection;

  public PublicSeatController(InventorySeatRepository seats) {
    this(seats, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public PublicSeatController(InventorySeatRepository seats, SeatBitmapProjection projection) {
    this.seats = seats;
    this.projection = projection;
  }

  @GetMapping("/{sessionId}/seats")
  public Map<String, Object> seats(@PathVariable("sessionId") String sessionId) {
    var rows = seats.listBySession(sessionId);
    if (projection != null) rows = projection.overlay(sessionId, rows);
    return ApiResponse.ok("ok", rows.stream()
        .map(InventoryViews::publicSeat)
        .toList());
  }
}

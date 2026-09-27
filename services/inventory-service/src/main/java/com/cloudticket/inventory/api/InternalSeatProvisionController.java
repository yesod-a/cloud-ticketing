package com.cloudticket.inventory.api;

import com.cloudticket.common.security.RequireInternalToken;
import com.cloudticket.inventory.persistence.InventorySeatRepository;
import com.cloudticket.inventory.cache.SeatBitmapProjection;
import com.cloudticket.inventory.persistence.entity.InventorySeatEntity;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Receives the seat layout activity-service derived from a venue template. */
@RestController
@RequireInternalToken
@RequestMapping("/api/internal/inventory")
public class InternalSeatProvisionController {

  private final InventorySeatRepository seats;
  private final SeatBitmapProjection projection;

  public InternalSeatProvisionController(InventorySeatRepository seats) {
    this(seats, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public InternalSeatProvisionController(InventorySeatRepository seats, SeatBitmapProjection projection) {
    this.seats = seats;
    this.projection = projection;
  }

  @PostMapping("/sessions/{sessionId}/seats")
  public Map<String, Object> provision(@PathVariable("sessionId") String sessionId,
                                       @RequestBody InventoryCommands.ProvisionSeats body) {
    List<InventorySeatEntity> replacement = body.toEntities(sessionId);
    seats.replaceSessionSeats(sessionId, replacement);
    if (projection != null) projection.rebuild(sessionId, replacement);
    return Map.of("code", "OK", "count", replacement.size());
  }
}

package com.cloudticket.inventory.api;

import com.cloudticket.inventory.persistence.InventorySeatRepository;
import com.cloudticket.inventory.persistence.entity.InventorySeatEntity;
import org.springframework.stereotype.Component;

/**
 * Resolves seat attributes for {@code @RequireScope} and {@code @AuditAction} expressions.
 *
 * <p>Scoping an inventory change needs the session and activity a seat belongs to, and the audit
 * needs the status it had before the call — none of which are request parameters.
 */
@Component("inventorySeats")
public class InventorySeatLookup {

  private final InventorySeatRepository seats;

  public InventorySeatLookup(InventorySeatRepository seats) {
    this.seats = seats;
  }

  public String session(String seatId) {
    return seats.find(seatId).map(InventorySeatEntity::getSessionId).orElse(null);
  }

  public String activity(String seatId) {
    return seats.find(seatId).map(InventorySeatEntity::getActivityId).orElse(null);
  }

  public String status(String seatId) {
    return seats.find(seatId).map(InventorySeatEntity::getStatus).orElse(null);
  }
}

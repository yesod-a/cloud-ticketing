package com.cloudticket.inventory.api;

import com.cloudticket.common.security.AuditAction;
import com.cloudticket.common.security.CallerContext;
import com.cloudticket.common.security.CallerContextHolder;
import com.cloudticket.common.security.RequireInternalToken;
import com.cloudticket.common.security.RequirePermission;
import com.cloudticket.common.security.RequireScope;
import com.cloudticket.common.security.ResourceScopeRule;
import com.cloudticket.common.web.PageResult;
import com.cloudticket.inventory.persistence.InventorySeatRepository;
import com.cloudticket.inventory.cache.SeatBitmapProjection;
import com.cloudticket.inventory.security.InventoryAuthorization;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inventory administration.
 *
 * <p>These endpoints are reached through the gateway, which forwards the operator identity; the
 * aspect enforces the internal token, the permission, the seat scope and the audit record in one
 * place instead of in each method body.
 */
@RestController
@RequireInternalToken
@RequestMapping("/api/admin/inventory")
public class InternalInventoryController {

  private final InventorySeatRepository seats;
  private final SeatBitmapProjection projection;
  private final InventoryAuthorization policy = new InventoryAuthorization();

  public InternalInventoryController(InventorySeatRepository seats) {
    this(seats, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public InternalInventoryController(InventorySeatRepository seats, SeatBitmapProjection projection) {
    this.seats = seats;
    this.projection = projection;
  }

  @RequirePermission("inventory:read")
  @GetMapping
  public Map<String, Object> list(@RequestParam(name = "sessionId", defaultValue = "") String sessionId,
                                  @RequestParam(name = "status", defaultValue = "") String status,
                                  @RequestParam(name = "page", defaultValue = "0") int page,
                                  @RequestParam(name = "size", defaultValue = "20") int size) {
    CallerContext caller = CallerContextHolder.current();
    PageResult<com.cloudticket.inventory.persistence.entity.InventorySeatEntity> result =
        ResourceScopeRule.contains(caller.permissions(), "system:config")
            ? seats.page(sessionId, status, page, size)
            : seats.pageScoped(sessionId, status, caller.scopes(), page, size);
    List<Map<String, Object>> items = result.items().stream().map(InventoryViews::adminSeat).toList();
    return new PageResult<>(items, result.page(), result.size(), result.total()).asMap();
  }

  @RequirePermission("inventory:adjust")
  @RequireScope(type = "SESSION", id = "@inventorySeats.session(#id)", parent = "@inventorySeats.activity(#id)")
  @AuditAction(action = "SEAT_STATUS_ADJUSTED", resourceType = "INVENTORY_SEAT", resourceId = "#id",
      before = "@inventorySeats.status(#id)", after = "#result['status']", reason = "#body.reason()")
  @PutMapping("/seats/{id}")
  public Map<String, Object> adjust(@PathVariable("id") String id,
                                    @RequestBody InventoryCommands.AdjustSeat body) {
    if (!policy.canAdjust(CallerContextHolder.current().permissions(), body.reason())) {
      throw new SecurityException("forbidden");
    }
    String status = body.statusOrDefault();
    if (!InventorySeatRepository.STATUSES.contains(status)) {
      throw new IllegalArgumentException("invalid status");
    }
    seats.adjust(id, status);
    if (projection != null) {
      seats.find(id).map(com.cloudticket.inventory.persistence.entity.InventorySeatEntity::getSessionId)
          .ifPresent(projection::invalidate);
    }
    return Map.of("id", id, "status", status);
  }

  @RequirePermission("inventory:lock-release")
  @RequireScope(type = "SESSION", id = "@inventorySeats.session(#id)", parent = "@inventorySeats.activity(#id)")
  @AuditAction(action = "SEAT_LOCK_RELEASED", resourceType = "INVENTORY_SEAT", resourceId = "#id",
      before = "@inventorySeats.status(#id)", after = "'AVAILABLE'", reason = "#body.reason()")
  @PostMapping("/locks/{id}/release")
  public Map<String, Object> release(@PathVariable("id") String id,
                                     @RequestBody InventoryCommands.AdjustSeat body) {
    if (!policy.canRelease(CallerContextHolder.current().permissions(), body.reason())) {
      throw new SecurityException("forbidden");
    }
    seats.adjust(id, InventorySeatRepository.AVAILABLE);
    if (projection != null) {
      seats.find(id).map(com.cloudticket.inventory.persistence.entity.InventorySeatEntity::getSessionId)
          .ifPresent(projection::invalidate);
    }
    return Map.of("id", id, "status", InventorySeatRepository.AVAILABLE);
  }
}

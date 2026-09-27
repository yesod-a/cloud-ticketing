package com.cloudticket.activity.api;

import com.cloudticket.activity.api.command.VenueCommands;
import com.cloudticket.activity.domain.SeatLayoutRules;
import com.cloudticket.activity.domain.Venue;
import com.cloudticket.activity.domain.VenueSeat;
import com.cloudticket.activity.service.VenueService;
import com.cloudticket.common.security.AuditAction;
import com.cloudticket.common.security.CallerContext;
import com.cloudticket.common.security.CallerContextHolder;
import com.cloudticket.common.security.RequirePermission;
import com.cloudticket.common.web.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/venues")
public class VenueAdminController {

  private final VenueService venues;

  public VenueAdminController(VenueService venues) {
    this.venues = venues;
  }

  @RequirePermission({"venue:read"})
  @GetMapping
  public Map<String, Object> list(@RequestParam(name = "keyword", defaultValue = "") String keyword,
                                  @RequestParam(name = "activityId", defaultValue = "") String activityId,
                                  @RequestParam(name = "page", defaultValue = "0") int page,
                                  @RequestParam(name = "size", defaultValue = "20") int size) {
    if (activityId != null && !activityId.isBlank()) {
      CallerContext caller = CallerContextHolder.current();
      return Map.of("items", venues.list(activityId, caller.permissions(), caller.scopes()));
    }
    return venues.page(keyword, page, size).asMap();
  }

  // These endpoints answer with the shared code/message/traceId/data envelope, so the audit
  // expressions read the payload back out of it.
  @RequirePermission({"activity:write", "venue:write"})
  @AuditAction(action = "VENUE_CREATED", resourceType = "VENUE", resourceId = "#result['data'].id()",
      after = "#result['data']")
  @PostMapping
  public Map<String, Object> create(@RequestBody VenueCommands.CreateVenue body) {
    Venue venue = venues.create(body.activityId(), body.name(), body.address());
    return ApiResponse.ok("created", CallerContextHolder.current().traceId(), venue);
  }

  @RequirePermission({"activity:write", "venue:write"})
  @AuditAction(action = "VENUE_UPDATED", resourceType = "VENUE", resourceId = "#venueId",
      before = "@activitySnapshots.venue(#venueId)", after = "#result['data']")
  @PutMapping("/{venueId}")
  public Map<String, Object> update(@PathVariable("venueId") String venueId,
                                    @RequestBody VenueCommands.UpdateVenue body) {
    return ApiResponse.ok("updated", CallerContextHolder.current().traceId(),
        venues.update(venueId, body.name(), body.address()));
  }

  @RequirePermission({"seat-layout:write", "venue:read"})
  @GetMapping("/{venueId}/seats")
  public Map<String, Object> seats(@PathVariable("venueId") String venueId) {
    return Map.of("items", venues.seats(venueId));
  }

  @RequirePermission("seat-layout:write")
  @AuditAction(action = "VENUE_SEAT_CREATED", resourceType = "VENUE_SEAT",
      resourceId = "#result['data'].id()", after = "#result['data']")
  @PostMapping("/{venueId}/seats")
  public Map<String, Object> createSeat(@PathVariable("venueId") String venueId,
                                        @RequestBody VenueCommands.CreateVenueSeat body) {
    VenueSeat seat = venues.createSeat(venueId, body.areaLabel(), body.rowLabel(), body.seatNumberOrZero(),
        body.displayName(), body.xOrZero(), body.yOrZero(), body.seatType(), body.enabledOrDefault());
    return ApiResponse.ok("created", CallerContextHolder.current().traceId(), seat);
  }

  /**
   * Regenerates the venue layout through the strategy chosen by {@code mode}. The request body is the
   * same object the strategies consume, so a new layout type only adds a strategy bean.
   */
  @RequirePermission("seat-layout:write")
  @AuditAction(action = "VENUE_LAYOUT_GENERATED", resourceType = "VENUE", resourceId = "#venueId",
      after = "#result['data'].size() + ' seats'")
  @PostMapping("/{venueId}/layout")
  public Map<String, Object> layout(@PathVariable("venueId") String venueId, @RequestBody SeatLayoutRules body) {
    return ApiResponse.ok("generated", CallerContextHolder.current().traceId(),
        venues.generateLayout(venueId, body));
  }

  @RequirePermission("seat-layout:write")
  @AuditAction(action = "VENUE_SEAT_UPDATED", resourceType = "VENUE_SEAT", resourceId = "#seatId",
      after = "#result['data']")
  @PutMapping("/{venueId}/seats/{seatId}")
  public Map<String, Object> updateSeat(@PathVariable("venueId") String venueId,
                                        @PathVariable("seatId") String seatId,
                                        @RequestBody VenueCommands.UpdateVenueSeat body) {
    VenueSeat seat = venues.updateSeat(seatId, body.areaLabel(), body.rowLabel(), body.seatNumberOrZero(),
        body.displayName(), body.xOrZero(), body.yOrZero(), body.seatType(), body.enabledOrDefault(), body.status());
    return ApiResponse.ok("updated", CallerContextHolder.current().traceId(), seat);
  }

  @RequirePermission("seat-layout:write")
  @AuditAction(action = "VENUE_SEAT_DELETED", resourceType = "VENUE_SEAT", resourceId = "#seatId",
      before = "", after = "")
  @DeleteMapping("/{venueId}/seats/{seatId}")
  public Map<String, Object> deleteSeat(@PathVariable("venueId") String venueId,
                                        @PathVariable("seatId") String seatId) {
    venues.deleteSeat(seatId);
    return ApiResponse.code("OK");
  }

  @RequirePermission({"activity:write", "venue:write"})
  @AuditAction(action = "VENUE_DELETED", resourceType = "VENUE", resourceId = "#venueId",
      before = "", after = "")
  @DeleteMapping("/{venueId}")
  public Map<String, Object> deleteVenue(@PathVariable("venueId") String venueId) {
    venues.delete(venueId);
    return ApiResponse.code("OK");
  }
}

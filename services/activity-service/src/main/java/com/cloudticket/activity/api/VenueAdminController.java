package com.cloudticket.activity.api;
import com.cloudticket.activity.service.ActivityCatalog;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/admin/venues")
public class VenueAdminController {
  private final ActivityCatalog catalog;
  public VenueAdminController(ActivityCatalog catalog){this.catalog=catalog;}
  @GetMapping
  public Map<String,Object> list(@RequestParam(name="activityId",defaultValue="") String activityId,@RequestHeader(value="X-User-Permissions",defaultValue="") String permissions,@RequestHeader(value="X-User-Scopes",defaultValue="") String scopes){if(!permissions.contains("venue:read")&&!permissions.contains("system:config"))throw new SecurityException("forbidden");return Map.of("items",catalog.venues(activityId,permissions,scopes));}
  @PostMapping
  public Map<String,Object> createHttp(@RequestBody Map<String,String> body,@RequestHeader(value="X-User-Permissions",defaultValue="") String permissions,@RequestHeader(value="X-User-Scopes",defaultValue="") String scopes,@RequestHeader(value="X-User-Id",defaultValue="") String actor,@RequestHeader(value="X-Trace-Id",defaultValue="") String trace){if(!com.cloudticket.activity.security.ScopeAccess.allows(permissions,scopes,"ACTIVITY",body.get("activityId")))throw new SecurityException("forbidden");return create(body,permissions,actor,trace);}
  public Map<String,Object> create(Map<String,String> body,String permissions,String actor,String trace){if(!permissions.contains("activity:write")&&!permissions.contains("venue:write")&&!permissions.contains("system:config"))throw new SecurityException("forbidden");var venue=body.containsKey("capacity")?catalog.createVenue(body.get("activityId"),body.get("name"),body.get("address"),parseInt(body.get("capacity"))):catalog.createVenue(body.get("activityId"),body.get("name"),body.get("address"));catalog.audit(actor,"VENUE_CREATED","VENUE",venue.id(),null,venue.toString(),trace);return Map.of("code","OK","message","created","traceId",trace==null?"":trace,"data",venue);}
  @GetMapping("/{venueId}/seats")
  public Map<String,Object> seats(@PathVariable String venueId,@RequestHeader(value="X-User-Permissions",defaultValue="") String permissions){if(!permissions.contains("seat-layout:write")&&!permissions.contains("venue:read")&&!permissions.contains("system:config"))throw new SecurityException("forbidden");return Map.of("items",catalog.venueSeats(venueId));}
  @PostMapping("/{venueId}/seats")
  public Map<String,Object> createSeat(@PathVariable String venueId,@RequestBody Map<String,String> body,@RequestHeader(value="X-User-Permissions",defaultValue="") String permissions,@RequestHeader(value="X-User-Id",defaultValue="") String actor,@RequestHeader(value="X-Trace-Id",defaultValue="") String trace){if(!permissions.contains("seat-layout:write")&&!permissions.contains("system:config"))throw new SecurityException("forbidden");var seat=catalog.createVenueSeat(venueId,body.get("rowLabel"),parseInt(body.get("seatNumber")),body.get("position"),body.get("status"));catalog.audit(actor,"VENUE_SEAT_CREATED","VENUE_SEAT",seat.id(),null,seat.toString(),trace);return Map.of("code","OK","data",seat);}
  @PutMapping("/{venueId}/seats/{seatId}")
  public Map<String,Object> updateSeat(@PathVariable String venueId,@PathVariable String seatId,@RequestBody Map<String,String> body,@RequestHeader(value="X-User-Permissions",defaultValue="") String permissions,@RequestHeader(value="X-User-Id",defaultValue="") String actor,@RequestHeader(value="X-Trace-Id",defaultValue="") String trace){if(!permissions.contains("seat-layout:write")&&!permissions.contains("system:config"))throw new SecurityException("forbidden");var seat=catalog.updateVenueSeat(seatId,body.get("rowLabel"),parseInt(body.get("seatNumber")),body.get("position"),body.get("status"));catalog.audit(actor,"VENUE_SEAT_UPDATED","VENUE_SEAT",seat.id(),null,seat.toString(),trace);return Map.of("code","OK","data",seat);}
  @DeleteMapping("/{venueId}/seats/{seatId}")
  public Map<String,Object> deleteSeat(@PathVariable String venueId,@PathVariable String seatId,@RequestHeader(value="X-User-Permissions",defaultValue="") String permissions,@RequestHeader(value="X-User-Id",defaultValue="") String actor,@RequestHeader(value="X-Trace-Id",defaultValue="") String trace){if(!permissions.contains("seat-layout:write")&&!permissions.contains("system:config"))throw new SecurityException("forbidden");catalog.deleteVenueSeat(seatId);catalog.audit(actor,"VENUE_SEAT_DELETED","VENUE_SEAT",seatId,null,null,trace);return Map.of("code","OK");}
  private int parseInt(String value){try{return value==null||value.isBlank()?0:Integer.parseInt(value);}catch(NumberFormatException e){throw new IllegalArgumentException("invalid number");}}
}

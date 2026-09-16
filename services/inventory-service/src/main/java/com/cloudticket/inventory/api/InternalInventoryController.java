package com.cloudticket.inventory.api;

import com.cloudticket.inventory.security.InventoryAuthorization;
import com.cloudticket.inventory.security.InventoryScopeAccess;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/inventory")
public class InternalInventoryController {
  private final InventoryAuthorization policy = new InventoryAuthorization();
  private final JdbcTemplate jdbc;
  private final String internalToken;
  public InternalInventoryController(JdbcTemplate jdbc, @Value("${cloudticket.internal-service-token:dev-internal-token}") String internalToken) { this.jdbc = jdbc; this.internalToken = internalToken; }
  private void trusted(String token) { if (token == null || !token.equals(internalToken)) throw new SecurityException("internal authentication required"); }
  private void allowed(String permissions, String required) { if (permissions == null || !permissions.contains(required)) throw new SecurityException("forbidden"); }

  @GetMapping
  public Map<String,Object> list(@RequestParam(name="sessionId",defaultValue="") String sessionId, @RequestParam(name="status",defaultValue="") String status, @RequestParam(name="page",defaultValue="0") int page, @RequestParam(name="size",defaultValue="20") int size, @RequestHeader(value="X-User-Permissions",defaultValue="") String permissions, @RequestHeader(value="X-User-Scopes",defaultValue="") String scopes, @RequestHeader(value="X-Internal-Service-Token",defaultValue="") String token) {
    trusted(token); allowed(permissions, "inventory:read");
    int p=Math.max(0,page), s=Math.min(100,Math.max(1,size)); String sid=sessionId.trim(), st=status.trim();
    boolean unrestricted = permissions.contains("system:config");
    String scopeClause = unrestricted ? "" : " AND (FIND_IN_SET(CONCAT('SESSION:',session_id),REPLACE(?,' ',''))>0 OR FIND_IN_SET(CONCAT('ACTIVITY:',activity_id),REPLACE(?,' ',''))>0 OR FIND_IN_SET('SESSION:*',REPLACE(?,' ',''))>0 OR FIND_IN_SET('ACTIVITY:*',REPLACE(?,' ',''))>0)";
    Object[] filterArgs = unrestricted ? new Object[]{sid,sid,st,st} : new Object[]{sid,sid,st,st,scopes,scopes,scopes,scopes};
    Integer total=jdbc.queryForObject("SELECT COUNT(*) FROM inventory_seat WHERE (?='' OR session_id=?) AND (?='' OR status=?)"+scopeClause,Integer.class,filterArgs);
    Object[] listArgs = unrestricted ? new Object[]{sid,sid,st,st,s,p*s} : new Object[]{sid,sid,st,st,scopes,scopes,scopes,scopes,s,p*s};
    var items=jdbc.query("SELECT id,session_id,row_label,seat_number,status,updated_at FROM inventory_seat WHERE (?='' OR session_id=?) AND (?='' OR status=?)"+scopeClause+" ORDER BY session_id,row_label,seat_number LIMIT ? OFFSET ?",(r,n)->Map.of("id",r.getString("id"),"sessionId",r.getString("session_id"),"row",r.getString("row_label"),"number",r.getInt("seat_number"),"status",r.getString("status"),"updatedAt",r.getTimestamp("updated_at").toInstant().toString()),listArgs);
    return page(items,p,s,total==null?0:total);
  }

  @PutMapping("/seats/{id}")
  public Map<String,Object> adjust(@PathVariable("id") String id,@RequestBody Map<String,String> body,@RequestHeader(value="X-User-Permissions",defaultValue="") String permissions,@RequestHeader(value="X-User-Scopes",defaultValue="") String scopes,@RequestHeader(value="X-Internal-Service-Token",defaultValue="") String token,@RequestHeader(value="X-User-Id",defaultValue="") String actor,@RequestHeader(value="X-Trace-Id",defaultValue="") String trace) {
    trusted(token); if(!policy.canAdjust(permissions,body.get("reason"))) throw new SecurityException("forbidden"); SeatRef seat=seat(id); if(seat==null||!InventoryScopeAccess.allows(permissions,scopes,seat.sessionId(),seat.activityId())) throw new SecurityException("forbidden"); String status=body.getOrDefault("status","AVAILABLE"); if(!Set.of("AVAILABLE","LOCKED","SOLD","DISABLED").contains(status)) throw new IllegalArgumentException("invalid status"); String before=seat.status(); jdbc.update("UPDATE inventory_seat SET status=? WHERE id=?",status,id); audit(actor,"SEAT_STATUS_ADJUSTED",id,before,status,body.get("reason"),trace); return Map.of("id",id,"status",status);
  }

  @PostMapping("/locks/{id}/release")
  public Map<String,Object> release(@PathVariable("id") String id,@RequestHeader(value="X-User-Permissions",defaultValue="") String permissions,@RequestHeader(value="X-User-Scopes",defaultValue="") String scopes,@RequestHeader(value="X-Internal-Service-Token",defaultValue="") String token,@RequestHeader(value="X-User-Id",defaultValue="") String actor,@RequestHeader(value="X-Trace-Id",defaultValue="") String trace,@RequestBody Map<String,String> body) { trusted(token); if(!policy.canRelease(permissions,body.get("reason"))) throw new SecurityException("forbidden"); SeatRef seat=seat(id); if(seat==null||!InventoryScopeAccess.allows(permissions,scopes,seat.sessionId(),seat.activityId())) throw new SecurityException("forbidden"); String before=seat.status(); jdbc.update("UPDATE inventory_seat SET status='AVAILABLE' WHERE id=?",id); audit(actor,"SEAT_LOCK_RELEASED",id,before,"AVAILABLE",body.get("reason"),trace); return Map.of("id",id,"status","AVAILABLE"); }
  private String currentStatus(String id) { return jdbc.query("SELECT status FROM inventory_seat WHERE id=?", (r,n) -> r.getString(1), id).stream().findFirst().orElse(""); }
  private SeatRef seat(String id) { return jdbc.query("SELECT session_id,activity_id,status FROM inventory_seat WHERE id=?",(r,n)->new SeatRef(r.getString("session_id"),r.getString("activity_id"),r.getString("status")),id).stream().findFirst().orElse(null); }
  private record SeatRef(String sessionId,String activityId,String status) {}
  private void audit(String actor,String action,String resourceId,String before,String after,String reason,String trace) { jdbc.update("INSERT INTO inventory_audit_log(id,actor_user_id,action,resource_id,before_status,after_status,reason,trace_id) VALUES(?,NULLIF(?,''),?,?,?,?,?,?)",UUID.randomUUID().toString(),actor,action,resourceId,before,after,reason,trace); }
  private Map<String,Object> page(List<?> items,int page,int size,long total){return Map.of("items",items,"page",page,"size",size,"total",total,"totalPages",total==0?0:(total+size-1)/size);}
}

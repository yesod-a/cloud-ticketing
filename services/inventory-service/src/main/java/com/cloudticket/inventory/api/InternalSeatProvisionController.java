package com.cloudticket.inventory.api;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/inventory")
public class InternalSeatProvisionController {
  private final JdbcTemplate jdbc;
  private final String internalToken;
  public InternalSeatProvisionController(JdbcTemplate jdbc, @Value("${cloudticket.internal-service-token:dev-internal-token}") String internalToken) { this.jdbc = jdbc; this.internalToken = internalToken; }
  private void trusted(String token) { if (token == null || !token.equals(internalToken)) throw new SecurityException("internal authentication required"); }
  private String str(Map<?,?> m, String key) { Object v = m.get(key); return v == null ? "" : String.valueOf(v); }
  private int parseInt(Map<?,?> m, String key) { String v = str(m, key); try { return v.isBlank() ? 0 : Integer.parseInt(v); } catch (NumberFormatException e) { return 0; } }
  private java.math.BigDecimal dec(Map<?,?> m, String key) { String v = str(m, key); try { return v.isBlank() ? null : new java.math.BigDecimal(v); } catch (NumberFormatException e) { return null; } }
  @PostMapping("/sessions/{sessionId}/seats")
  public Map<String,Object> provision(@PathVariable("sessionId") String sessionId, @RequestBody Map<String,Object> body, @RequestHeader(value="X-Internal-Service-Token",defaultValue="") String token) {
    trusted(token);
    String activityId = String.valueOf(body.getOrDefault("activityId", ""));
    Object raw = body.get("seats");
    jdbc.update("DELETE FROM inventory_seat WHERE session_id=?", sessionId);
    int count = 0;
    if (raw instanceof List<?> list) {
      for (Object item : list) {
        if (!(item instanceof Map<?,?> m)) continue;
        jdbc.update("INSERT INTO inventory_seat (id,session_id,activity_id,area_label,row_label,seat_number,display_name,seat_type,position_x,position_y,status) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
          str(m,"id"), sessionId, activityId.isBlank() ? null : activityId, str(m,"areaLabel"), str(m,"rowLabel"), parseInt(m,"seatNumber"), str(m,"displayName"), str(m,"seatType"), dec(m,"x"), dec(m,"y"), str(m,"status"));
        count++;
      }
    }
    return Map.of("code","OK","count",count);
  }
}

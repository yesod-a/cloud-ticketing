package com.cloudticket.inventory.api;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
public class PublicSeatController {
  private final JdbcTemplate jdbc;
  public PublicSeatController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  @GetMapping("/{sessionId}/seats")
  public Map<String,Object> seats(@PathVariable("sessionId") String sessionId) {
    var items = jdbc.query("SELECT id,row_label,seat_number,status FROM inventory_seat WHERE session_id=? ORDER BY row_label,seat_number", (r,n) -> Map.of("id",r.getString("id"),"row",r.getString("row_label"),"number",r.getInt("seat_number"),"status",r.getString("status")), sessionId);
    return Map.of("code","OK","message","ok","traceId","","data",items);
  }
}

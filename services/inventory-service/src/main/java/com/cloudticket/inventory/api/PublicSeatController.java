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
    var items = jdbc.query("SELECT id,area_label,row_label,seat_number,display_name,position_x,position_y,seat_type,status FROM inventory_seat WHERE session_id=? ORDER BY area_label,position_y,position_x,row_label,seat_number", (r,n) -> Map.of("id",r.getString("id"),"areaLabel",r.getString("area_label"),"row",r.getString("row_label"),"number",r.getInt("seat_number"),"displayName",r.getString("display_name"),"x",r.getBigDecimal("position_x")==null?0:r.getBigDecimal("position_x").intValue(),"y",r.getBigDecimal("position_y")==null?0:r.getBigDecimal("position_y").intValue(),"type",r.getString("seat_type"),"status",r.getString("status")), sessionId);
    return Map.of("code","OK","message","ok","traceId","","data",items);
  }
}

package com.cloudticket.auth.api;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/auth/token")
public class AuthTokenStatusController {
  private final JdbcTemplate jdbc;
  private final String internalToken;

  public AuthTokenStatusController(JdbcTemplate jdbc, @Value("${cloudticket.internal-service-token:dev-internal-token}") String internalToken) {
    this.jdbc = jdbc;
    this.internalToken = internalToken;
  }

  @GetMapping("/status")
  public Map<String, Object> status(@RequestHeader(value = "X-Internal-Service-Token", defaultValue = "") String token, @RequestParam String userId, @RequestParam String scopeVersion, @RequestParam String jti) {
    trusted(token);
    var rows = jdbc.query("SELECT status,scope_version FROM auth_user WHERE id=UUID_TO_BIN(?) AND NOT EXISTS (SELECT 1 FROM auth_revoked_access_token r WHERE r.jti=? AND r.expires_at>CURRENT_TIMESTAMP(6))", (r, n) -> Map.of("status", r.getString("status"), "scopeVersion", r.getLong("scope_version")), userId, jti);
    if (rows.isEmpty()) return Map.of("active", false);
    var row = rows.get(0);
    return Map.of("active", "ACTIVE".equals(row.get("status")) && String.valueOf(row.get("scopeVersion")).equals(scopeVersion));
  }


  private void trusted(String token) { if (token == null || !token.equals(internalToken)) throw new SecurityException("internal authentication required"); }
}

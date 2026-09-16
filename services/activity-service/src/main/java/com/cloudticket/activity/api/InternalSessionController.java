package com.cloudticket.activity.api;

import com.cloudticket.activity.service.ActivityCatalog;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/sessions")
public class InternalSessionController {
  private final ActivityCatalog catalog;
  private final String internalToken;
  public InternalSessionController(ActivityCatalog catalog, @Value("${cloudticket.internal-service-token:dev-internal-token}") String internalToken) { this.catalog = catalog; this.internalToken = internalToken; }
  private void trusted(String token) { if (token == null || !token.equals(internalToken)) throw new SecurityException("internal authentication required"); }
  @GetMapping("/{sessionId}")
  public Map<String,Object> session(@PathVariable("sessionId") String sessionId, @RequestHeader(value="X-Internal-Service-Token",defaultValue="") String token) {
    trusted(token);
    var session = catalog.getSession(sessionId);
    return Map.of("id", session.id(), "activityId", session.activityId(), "status", session.status(), "priceMinor", session.priceMinor());
  }
}

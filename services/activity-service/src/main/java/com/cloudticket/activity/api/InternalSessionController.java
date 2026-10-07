package com.cloudticket.activity.api;

import com.cloudticket.activity.domain.Session;
import com.cloudticket.activity.service.SessionService;
import com.cloudticket.common.security.RequireInternalToken;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal session lookup used by order-service to price an order. */
@RestController
@RequireInternalToken
@RequestMapping("/api/internal/sessions")
public class InternalSessionController {

  private final SessionService sessions;

  public InternalSessionController(SessionService sessions) {
    this.sessions = sessions;
  }

  @GetMapping("/{sessionId}")
  public Map<String, Object> session(@PathVariable("sessionId") String sessionId) {
    Session session = sessions.require(sessionId);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", session.id());
    payload.put("activityId", session.activityId());
    payload.put("status", session.status());
    payload.put("priceMinor", session.priceMinor());
    payload.put("layoutMode", session.layoutMode());
    payload.put("purchaseLimit", session.purchaseLimit());
    payload.put("capacity", session.capacity());
    payload.put("saleMode", session.saleMode());
    return payload;
  }
}

package com.cloudticket.order.client;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

@Component
public class ActivitySessionClient {
  private static final ParameterizedTypeReference<Map<String, Object>> SESSION_RESPONSE =
      new ParameterizedTypeReference<>() {};

  private final InternalServiceClient internal;
  private final String baseUrl;

  public ActivitySessionClient(
      InternalServiceClient internal,
      @Value("${cloudticket.activity-base-url:http://activity-service:8081}") String baseUrl) {
    this.internal = internal;
    this.baseUrl = baseUrl;
  }

  public int priceMinor(String sessionId) {
    Map<String, Object> body = sessionBody(sessionId);
    if (body == null || !(body.get("priceMinor") instanceof Number price)) throw new IllegalStateException("session price unavailable");
    return Math.max(0, price.intValue());
  }

  public SessionInfo session(String sessionId) {
    Map<String, Object> body = sessionBody(sessionId);
    return new SessionInfo(String.valueOf(body.getOrDefault("layoutMode", "GRID")),
        body.get("priceMinor") instanceof Number n ? n.intValue() : 0,
        body.get("purchaseLimit") instanceof Number n ? n.intValue() : 0,
        String.valueOf(body.getOrDefault("status", "DRAFT")));
  }

  private Map<String, Object> sessionBody(String sessionId) {
    return internal.get(baseUrl, "/api/internal/sessions/{sessionId}", SESSION_RESPONSE, sessionId);
  }

  public record SessionInfo(String layoutMode, int priceMinor, int purchaseLimit, String status) {
    public SessionInfo(String layoutMode, int priceMinor, int purchaseLimit) {
      this(layoutMode, priceMinor, purchaseLimit, "ONSALE");
    }
  }
}

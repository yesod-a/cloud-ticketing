package com.cloudticket.order.client;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ActivitySessionClient {
  private final RestClient client;
  private final String internalToken;
  public ActivitySessionClient(RestClient.Builder builder, @Value("${cloudticket.activity-base-url:http://activity-service:8081}") String baseUrl, @Value("${cloudticket.internal-service-token:dev-internal-token}") String internalToken) {
    this.client = builder.baseUrl(baseUrl).build();
    this.internalToken = internalToken;
  }
  @SuppressWarnings("unchecked")
  public int priceMinor(String sessionId) {
    Map<String, Object> body = client.get().uri("/api/internal/sessions/{sessionId}", sessionId)
        .header("X-Internal-Service-Token", internalToken)
        .retrieve().body(Map.class);
    if (body == null || !(body.get("priceMinor") instanceof Number price)) throw new IllegalStateException("session price unavailable");
    return Math.max(0, price.intValue());
  }
}

package com.cloudticket.activity.client;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InventorySeatProvisionClient {
  private final RestClient client;
  private final String internalToken;
  public InventorySeatProvisionClient(RestClient.Builder builder, @Value("${cloudticket.inventory-base-url:http://inventory-service:8082}") String baseUrl, @Value("${cloudticket.internal-service-token:dev-internal-token}") String internalToken) {
    this.client = builder.baseUrl(baseUrl).build();
    this.internalToken = internalToken;
  }
  public void provision(String sessionId, String activityId, List<Map<String,Object>> seats) {
    client.post().uri("/api/internal/inventory/sessions/{sessionId}/seats", sessionId)
      .header("X-Internal-Service-Token", internalToken)
      .contentType(MediaType.APPLICATION_JSON)
      .body(Map.of("activityId", activityId == null ? "" : activityId, "seats", seats))
      .retrieve().toBodilessEntity();
  }

  public void provisionAdmission(String sessionId, int capacity) {
    client.post().uri("/api/internal/inventory/admission/ensure")
      .header("X-Internal-Service-Token", internalToken)
      .contentType(MediaType.APPLICATION_JSON)
      .body(Map.of("sessionId", sessionId, "capacity", capacity))
      .retrieve().toBodilessEntity();
  }

  public int remainingAdmission(String sessionId, int fallback) {
    try {
      Map<String, Object> body = client.get().uri("/api/internal/inventory/admission/sessions/{sessionId}", sessionId)
          .header("X-Internal-Service-Token", internalToken).retrieve()
          .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
      Object value = body == null ? null : body.get("remainingCapacity");
      int remaining = value instanceof Number n ? n.intValue() : fallback;
      return Math.max(0, remaining);
    } catch (RuntimeException ignored) {
      return fallback;
    }
  }
}

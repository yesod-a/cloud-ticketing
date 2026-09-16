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
}

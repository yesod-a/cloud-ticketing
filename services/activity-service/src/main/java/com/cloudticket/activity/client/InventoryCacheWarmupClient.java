package com.cloudticket.activity.client;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InventoryCacheWarmupClient {
  private final RestClient client;
  private final String internalToken;

  public InventoryCacheWarmupClient(RestClient.Builder builder,
                                    @Value("${cloudticket.inventory-base-url:http://inventory-service:8082}") String baseUrl,
                                    @Value("${cloudticket.internal-service-token:dev-internal-token}") String internalToken) {
    this.client = builder.baseUrl(baseUrl).build();
    this.internalToken = internalToken;
  }

  public void upsert(String sessionId, String saleStartAt) {
    if (sessionId == null || sessionId.isBlank() || saleStartAt == null || saleStartAt.isBlank()) return;
    client.post().uri("/api/internal/inventory/cache-warmups")
        .header("X-Internal-Service-Token", internalToken)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("sessionId", sessionId, "saleStartAt", saleStartAt))
        .retrieve().toBodilessEntity();
  }
}

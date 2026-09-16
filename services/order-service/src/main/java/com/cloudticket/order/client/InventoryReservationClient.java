package com.cloudticket.order.client;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@Component
public class InventoryReservationClient {
  private final RestClient client;
  private final String internalToken;

  public InventoryReservationClient(RestClient.Builder builder, @Value("${cloudticket.inventory-base-url:http://inventory-service:8082}") String baseUrl, @Value("${cloudticket.internal-service-token:dev-internal-token}") String internalToken) {
    this.client = builder.baseUrl(baseUrl).build();
    this.internalToken = internalToken;
  }

  public void reserve(String orderId, String sessionId, List<String> seatIds) {
    try {
      client.post().uri("/api/internal/inventory/locks").header("X-Internal-Service-Token", internalToken).contentType(MediaType.APPLICATION_JSON).body(Map.of("orderId", orderId, "sessionId", sessionId, "seatIds", seatIds, "ttlSeconds", 900)).retrieve().toBodilessEntity();
    } catch (HttpClientErrorException.Conflict conflict) {
      throw new SeatUnavailableException();
    }
  }

  public void release(String orderId) {
    client.post().uri("/api/internal/inventory/locks/{orderId}/release", orderId).header("X-Internal-Service-Token", internalToken).retrieve().toBodilessEntity();
  }

  public void confirm(String orderId) {
    client.post().uri("/api/internal/inventory/locks/{orderId}/confirm", orderId).header("X-Internal-Service-Token", internalToken).retrieve().toBodilessEntity();
  }

  @ResponseStatus(HttpStatus.CONFLICT)
  public static final class SeatUnavailableException extends RuntimeException {
    public SeatUnavailableException() { super("one or more seats are unavailable"); }
  }
}

package com.cloudticket.order.client;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@Component
public class InventoryReservationClient {
  private final InternalServiceClient internal;
  private final String baseUrl;

  public InventoryReservationClient(
      InternalServiceClient internal,
      @Value("${cloudticket.inventory-base-url:http://inventory-service:8082}") String baseUrl) {
    this.internal = internal;
    this.baseUrl = baseUrl;
  }

  public void reserve(String orderId, String sessionId, List<String> seatIds) {
    try {
      internal.post(baseUrl, "/api/internal/inventory/locks",
          Map.of("orderId", orderId, "sessionId", sessionId, "seatIds", seatIds, "ttlSeconds", 900));
    } catch (HttpClientErrorException.Conflict conflict) {
      throw new SeatUnavailableException();
    }
  }

  public void release(String orderId) {
    internal.post(baseUrl, "/api/internal/inventory/locks/{orderId}/release", null, orderId);
  }

  public void confirm(String orderId) {
    internal.post(baseUrl, "/api/internal/inventory/locks/{orderId}/confirm", null, orderId);
  }

  @ResponseStatus(HttpStatus.CONFLICT)
  public static final class SeatUnavailableException extends RuntimeException {
    public SeatUnavailableException() { super("one or more seats are unavailable"); }
  }
}

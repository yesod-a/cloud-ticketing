package com.cloudticket.order.client;

import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
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

  public List<Long> reserveQuantity(String orderId, String userId, String sessionId, int quantity) {
    try {
      Map<String, Object> response = internal.postForBody(baseUrl, "/api/internal/inventory/admission/reservations",
          Map.of("orderId", orderId, "userId", userId, "sessionId", sessionId, "quantity", quantity, "ttlSeconds", 900),
          new ParameterizedTypeReference<>() {});
      Object values = response == null ? null : response.get("ticketNumbers");
      if (values instanceof List<?> list) return list.stream().map(v -> ((Number) v).longValue()).toList();
      return List.of();
    } catch (HttpClientErrorException.Conflict conflict) { throw new SeatUnavailableException(); }
  }

  public void releaseQuantity(String orderId) { internal.post(baseUrl, "/api/internal/inventory/admission/reservations/{orderId}/release", null, orderId); }
  public void releaseRefunded(String orderId) { internal.post(baseUrl, "/api/internal/inventory/admission/reservations/{orderId}/refund-release", null, orderId); }
  public void confirmQuantity(String orderId) { internal.post(baseUrl, "/api/internal/inventory/admission/reservations/{orderId}/confirm", null, orderId); }

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

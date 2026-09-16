package com.cloudticket.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrderExpiryServiceTest {
  @Test
  void expiresOnlyPendingOrdersAndReleasesInventory() {
    var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
    var inventory = mock(com.cloudticket.order.client.InventoryReservationClient.class);
    when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(15)))
        .thenReturn(List.of(Map.of("id", "o-1", "userId", "u-1", "sessionId", "s-1", "seatIds", "A1")));
    when(jdbc.update(contains("SET status='EXPIRED'"), eq("o-1"), eq(15))).thenReturn(1);

    var service = new OrderExpiryService(jdbc, inventory, 15);
    assertEquals(1, service.expirePendingOrders());

    verify(inventory).release("o-1");
    verify(jdbc).update(contains("order_outbox"), any(), any(), any(), any(), any());
  }

  @Test
  void isIdempotentWhenConditionalUpdateDoesNotMatch() {
    var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
    var inventory = mock(com.cloudticket.order.client.InventoryReservationClient.class);
    when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(15)))
        .thenReturn(List.of(Map.of("id", "o-1", "userId", "u-1", "sessionId", "s-1", "seatIds", "A1")));
    when(jdbc.update(contains("SET status='EXPIRED'"), eq("o-1"), eq(15))).thenReturn(0);

    var service = new OrderExpiryService(jdbc, inventory, 15);
    assertEquals(0, service.expirePendingOrders());
    verifyNoInteractions(inventory);
  }
}

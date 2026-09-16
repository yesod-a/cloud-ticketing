package com.cloudticket.order;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

class OrderStoreTest {
  @Test
  void createPersistsOrderAndReturnsExistingOrderForSameIdempotencyKey() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    OrderStore store = new OrderStore(jdbc);
    when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq("idem-1")))
        .thenReturn(java.util.List.of(Map.of(
            "id", "existing", "userId", "user-1", "sessionId", "session-1",
            "seatIds", "A1,A2", "status", "PENDING")));

    Map<String, Object> result = store.create("user-1", "session-1", "A1,A2", "idem-1");

    assertEquals("existing", result.get("id"));
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void createRejectsBlankIdempotencyKey() {
    OrderStore store = new OrderStore(mock(JdbcTemplate.class));
    assertThrows(IllegalArgumentException.class, () -> store.create("u", "s", "A1", " "));
  }

  @Test
  void createRejectsSameIdempotencyKeyWithDifferentRequest() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    OrderStore store = new OrderStore(jdbc);
    when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq("idem-1")))
        .thenReturn(java.util.List.of(Map.of(
            "id", "existing", "userId", "user-1", "sessionId", "session-1",
            "seatIds", "A1", "status", "PENDING")));

    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> store.create("user-1", "session-2", "A1", "idem-1"));

    assertTrue(error.getMessage().contains("idempotency key reused"));
  }

  @Test
  void createWritesOrderCreatedEventToOutboxInSameStore() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenAnswer(invocation -> {
          String sql = invocation.getArgument(0);
          if (sql.contains("idempotency_key")) return java.util.List.of();
          return java.util.List.of(Map.of(
              "id", "order-1", "userId", "user-1", "sessionId", "session-1",
              "seatIds", "A1", "status", "PENDING",
              "createdAt", "2026-09-16T00:00:00Z", "updatedAt", "2026-09-16T00:00:00Z"));
        });
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

    Map<String, Object> result = new OrderStore(jdbc).create("user-1", "session-1", "A1", "idem-1");

    assertEquals("order-1", result.get("id"));
    verify(jdbc).update(contains("order_outbox"), any(), any(), any(), any(), any());
  }
}

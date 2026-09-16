package com.cloudticket.order;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

class ProcessedEventStoreTest {
  @Test
  void claimsAnEventOnlyOncePerConsumer() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

    boolean claimed = new ProcessedEventStore(jdbc).tryClaim("event-1", "ticket-service", "OrderCreated", "order-1", "hash", null);

    assertTrue(claimed);
    verify(jdbc).update(contains("processed_event"), any(), any(), any(), any(), any(), any());
  }

  @Test
  void duplicateEventClaimReturnsFalse() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new DuplicateKeyException("duplicate"));

    boolean claimed = new ProcessedEventStore(jdbc).tryClaim("event-1", "ticket-service", "OrderCreated", "order-1", "hash", null);

    assertFalse(claimed);
  }
}

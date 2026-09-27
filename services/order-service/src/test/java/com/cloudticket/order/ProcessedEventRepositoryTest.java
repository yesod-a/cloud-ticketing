package com.cloudticket.order;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.order.persistence.ProcessedEventRepository;
import com.cloudticket.order.persistence.mapper.ProcessedEventMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class ProcessedEventRepositoryTest {

  private final ProcessedEventMapper events = mock(ProcessedEventMapper.class);
  private final ProcessedEventRepository repository = new ProcessedEventRepository(events);

  @Test
  void claimsAnEventOnlyOncePerConsumer() {
    when(events.claim(any(), any(), any(), any(), any(), any())).thenReturn(1);

    assertTrue(repository.tryClaim("event-1", "ticket-service", "OrderCreated", "order-1", "hash", null));

    verify(events).claim("event-1", "ticket-service", "OrderCreated", "order-1", "hash", null);
  }

  @Test
  void duplicateEventClaimReturnsFalse() {
    when(events.claim(any(), any(), any(), any(), any(), any()))
        .thenThrow(new DuplicateKeyException("duplicate"));

    assertFalse(repository.tryClaim("event-1", "ticket-service", "OrderCreated", "order-1", "hash", null));
  }
}

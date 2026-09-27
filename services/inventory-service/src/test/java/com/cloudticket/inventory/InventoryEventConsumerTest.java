package com.cloudticket.inventory;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.inventory.event.InventoryEventConsumer;
import com.cloudticket.inventory.persistence.ProcessedInventoryEventRepository;
import com.cloudticket.inventory.service.InventoryReservationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InventoryEventConsumerTest {

  private final InventoryReservationService reservations = mock(InventoryReservationService.class);
  private final ProcessedInventoryEventRepository events = mock(ProcessedInventoryEventRepository.class);
  private final InventoryEventConsumer consumer =
      new InventoryEventConsumer(reservations, events, new ObjectMapper());

  @Test
  void paymentSucceededConfirmsInventory() {
    when(events.tryClaim("e-1", "PaymentSucceeded", "o-1", null)).thenReturn(true);
    consumer.consume("{\"eventId\":\"e-1\",\"eventType\":\"PaymentSucceeded\",\"aggregateId\":\"o-1\",\"payload\":{\"orderId\":\"o-1\"}}");

    verify(reservations).confirm("o-1");
  }

  @Test
  void cancellationReleasesInventory() {
    when(events.tryClaim("e-2", "OrderCancelled", "o-2", null)).thenReturn(true);
    consumer.consume("{\"eventId\":\"e-2\",\"eventType\":\"OrderCancelled\",\"aggregateId\":\"o-2\",\"payload\":{\"orderId\":\"o-2\"}}");

    verify(reservations).release("o-2");
  }

  @Test
  void duplicateEventIsIgnored() {
    when(events.tryClaim("e-3", "PaymentSucceeded", "o-3", null)).thenReturn(true, false);
    consumer.consume("{\"eventId\":\"e-3\",\"eventType\":\"PaymentSucceeded\",\"aggregateId\":\"o-3\",\"payload\":{\"orderId\":\"o-3\"}}");
    consumer.consume("{\"eventId\":\"e-3\",\"eventType\":\"PaymentSucceeded\",\"aggregateId\":\"o-3\",\"payload\":{\"orderId\":\"o-3\"}}");

    verify(reservations).confirm("o-3");
    verify(events, times(2)).tryClaim("e-3", "PaymentSucceeded", "o-3", null);
  }

  @Test
  void malformedEventDoesNotTouchInventory() {
    consumer.consume("{\"eventType\":\"PaymentSucceeded\",\"payload\":{}}");

    verify(reservations, never()).confirm(org.mockito.ArgumentMatchers.anyString());
    verify(reservations, never()).release(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void expiredAndRefundedEventsReleaseInventory() {
    when(events.tryClaim("e-4", "OrderExpired", "o-4", null)).thenReturn(true);
    when(events.tryClaim("e-5", "OrderRefunded", "o-5", null)).thenReturn(true);

    consumer.consume("{\"eventId\":\"e-4\",\"eventType\":\"OrderExpired\",\"aggregateId\":\"o-4\",\"payload\":{}}");
    consumer.consume("{\"eventId\":\"e-5\",\"eventType\":\"OrderRefunded\",\"aggregateId\":\"o-5\",\"payload\":{}}");

    verify(reservations).release("o-4");
    verify(reservations).release("o-5");
  }
}

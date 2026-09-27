package com.cloudticket.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.common.events.EventTypes;
import com.cloudticket.order.client.InventoryReservationClient;
import com.cloudticket.order.event.OutboxEventWriter;
import com.cloudticket.order.persistence.OrderRepository;
import com.cloudticket.order.persistence.entity.TicketOrderEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderExpiryServiceTest {

  private final OrderRepository orders = mock(OrderRepository.class);
  private final InventoryReservationClient inventory = mock(InventoryReservationClient.class);
  private final OutboxEventWriter outbox = mock(OutboxEventWriter.class);

  @Test
  void expiresOnlyPendingOrdersAndReleasesInventory() {
    when(orders.findExpiredCandidates(15)).thenReturn(List.of(candidate("o-1")));
    when(orders.expireIfStillPending("o-1", 15)).thenReturn(true);

    assertEquals(1, new OrderExpiryService(orders, inventory, outbox, 15).expirePendingOrders());

    verify(outbox).write(eq(EventTypes.ORDER_EXPIRED), eq("o-1"), any());
    verify(inventory).release("o-1");
  }

  @Test
  void isIdempotentWhenConditionalUpdateDoesNotMatch() {
    when(orders.findExpiredCandidates(15)).thenReturn(List.of(candidate("o-1")));
    when(orders.expireIfStillPending("o-1", 15)).thenReturn(false);

    assertEquals(0, new OrderExpiryService(orders, inventory, outbox, 15).expirePendingOrders());

    verify(outbox, never()).write(any(), any(), any());
    verify(inventory, never()).release(any());
  }

  @Test
  void keepsGoingWhenInventoryReleaseFails() {
    when(orders.findExpiredCandidates(15)).thenReturn(List.of(candidate("o-1")));
    when(orders.expireIfStillPending("o-1", 15)).thenReturn(true);
    org.mockito.Mockito.doThrow(new RuntimeException("inventory down")).when(inventory).release("o-1");

    assertEquals(1, new OrderExpiryService(orders, inventory, outbox, 15).expirePendingOrders());

    verify(outbox).write(eq(EventTypes.ORDER_EXPIRED), eq("o-1"), any());
  }

  private static TicketOrderEntity candidate(String id) {
    TicketOrderEntity order = new TicketOrderEntity();
    order.setId(id);
    order.setUserId("u-1");
    order.setSessionId("s-1");
    order.setSeatIds("A1");
    order.setStatus("PENDING");
    return order;
  }
}

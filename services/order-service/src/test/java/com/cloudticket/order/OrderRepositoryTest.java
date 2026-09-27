package com.cloudticket.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.common.events.EventTypes;
import com.cloudticket.order.client.ActivitySessionClient;
import com.cloudticket.order.client.InventoryReservationClient;
import com.cloudticket.order.event.OutboxEventWriter;
import com.cloudticket.order.persistence.OrderRepository;
import com.cloudticket.order.persistence.entity.TicketOrderEntity;
import com.cloudticket.order.persistence.mapper.TicketOrderMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OrderRepositoryTest {

  private final TicketOrderMapper orders = mock(TicketOrderMapper.class);
  private final InventoryReservationClient inventory = mock(InventoryReservationClient.class);
  private final ActivitySessionClient sessions = mock(ActivitySessionClient.class);
  private final OutboxEventWriter outbox = mock(OutboxEventWriter.class);
  private final OrderRepository repository = new OrderRepository(orders, inventory, sessions, outbox);

  @Test
  void createPersistsOrderAndReturnsExistingOrderForSameIdempotencyKey() {
    when(orders.selectOne(any())).thenReturn(existing("existing", "user-1", "session-1", "A1,A2"));

    TicketOrderEntity result = repository.create("user-1", "session-1", "A1,A2", "idem-1");

    assertEquals("existing", result.getId());
    verify(orders, never()).insert(any(TicketOrderEntity.class));
    verify(inventory, never()).reserve(anyString(), anyString(), any());
  }

  @Test
  void createRejectsBlankIdempotencyKey() {
    assertThrows(IllegalArgumentException.class, () -> repository.create("u", "s", "A1", " "));
  }

  @Test
  void createRejectsSeatSelectionOverTheSharedLimit() {
    assertThrows(IllegalArgumentException.class,
        () -> repository.create("u", "s", "A1,A2,A3,A4,A5,A6,A7", "idem-7"));
  }

  @Test
  void createRejectsSameIdempotencyKeyWithDifferentRequest() {
    when(orders.selectOne(any())).thenReturn(existing("existing", "user-1", "session-1", "A1"));

    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> repository.create("user-1", "session-2", "A1", "idem-1"));

    assert error.getMessage().contains("idempotency key reused");
  }

  @Test
  void createReservesSeatsPricesTheOrderAndWritesOrderCreatedEvent() {
    when(orders.selectOne(any())).thenReturn(null);
    when(sessions.priceMinor("session-1")).thenReturn(19_900);
    TicketOrderEntity[] persisted = new TicketOrderEntity[1];
    when(orders.insert(any(TicketOrderEntity.class))).thenAnswer(invocation -> {
      persisted[0] = invocation.getArgument(0);
      return 1;
    });
    when(orders.selectById(anyString())).thenAnswer(invocation -> persisted[0]);

    TicketOrderEntity result = repository.create("user-1", "session-1", "A1,A2", "idem-1");

    assertEquals(39_800, result.getAmountMinor());
    assertEquals("A1,A2", result.getSeatIds());
    assertEquals("PENDING", result.getStatus());
    verify(inventory).reserve(eq(result.getId()), eq("session-1"), eq(List.of("A1", "A2")));
    verify(outbox).write(eq(EventTypes.ORDER_CREATED), eq(result.getId()), any());
  }

  @Test
  void createReleasesTheReservationWhenTheInsertLosesTheIdempotencyRace() {
    when(orders.selectOne(any())).thenReturn(null).thenReturn(existing("winner", "user-1", "session-1", "A1"));
    when(orders.insert(any(TicketOrderEntity.class)))
        .thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate"));

    TicketOrderEntity result = repository.create("user-1", "session-1", "A1", "idem-1");

    assertEquals("winner", result.getId());
    ArgumentCaptor<String> reserved = ArgumentCaptor.forClass(String.class);
    verify(inventory).reserve(reserved.capture(), eq("session-1"), eq(List.of("A1")));
    verify(inventory).release(reserved.getValue());
  }

  @Test
  void cancelReleasesInventoryAndRecordsTheCancellation() {
    when(orders.cancelIfCancellable("order-1")).thenReturn(1);
    when(orders.selectById("order-1")).thenReturn(existingWithStatus("order-1", "CANCELLED"));

    TicketOrderEntity cancelled = repository.cancel("order-1");

    assertEquals("CANCELLED", cancelled.getStatus());
    verify(inventory).release("order-1");
    verify(outbox).write(eq(EventTypes.ORDER_CANCELLED), eq("order-1"), any());
  }

  @Test
  void cancelIsRejectedWhenTheConditionalUpdateDoesNotMatch() {
    when(orders.cancelIfCancellable("order-1")).thenReturn(0);

    assertThrows(IllegalStateException.class, () -> repository.cancel("order-1"));
    verify(inventory, never()).release(anyString());
  }

  private static TicketOrderEntity existing(String id, String userId, String sessionId, String seatIds) {
    TicketOrderEntity order = new TicketOrderEntity();
    order.setId(id);
    order.setUserId(userId);
    order.setSessionId(sessionId);
    order.setSeatIds(seatIds);
    order.setStatus("PENDING");
    order.setAmountMinor(0);
    return order;
  }

  private static TicketOrderEntity existingWithStatus(String id, String status) {
    TicketOrderEntity order = existing(id, "user-1", "session-1", "A1");
    order.setStatus(status);
    return order;
  }

}

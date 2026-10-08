package com.cloudticket.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.cloudticket.order.persistence.UserSessionPurchaseRepository;
import com.cloudticket.order.persistence.entity.TicketOrderEntity;
import com.cloudticket.order.persistence.mapper.TicketOrderMapper;
import com.cloudticket.order.timeout.OrderTimeoutOutboxWriter;
import com.cloudticket.order.OrderTimeoutZsetService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class OrderRepositoryTest {

  private final TicketOrderMapper orders = mock(TicketOrderMapper.class);
  private final InventoryReservationClient inventory = mock(InventoryReservationClient.class);
  private final ActivitySessionClient sessions = mock(ActivitySessionClient.class);
  private final OutboxEventWriter outbox = mock(OutboxEventWriter.class);
  private final OrderTimeoutOutboxWriter timeoutOutbox = mock(OrderTimeoutOutboxWriter.class);
  private final UserSessionPurchaseRepository purchases = mock(UserSessionPurchaseRepository.class);
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
  void createRejectsSessionThatIsNotOnSale() {
    when(sessions.session("session-1")).thenReturn(new ActivitySessionClient.SessionInfo("GRID", 19_900, 0, "DRAFT"));

    assertThrows(IllegalStateException.class, () -> repository.create("user-1", "session-1", "A1", "idem-draft"));
    verify(inventory).reserve(anyString(), eq("session-1"), eq(List.of("A1")));
    verify(inventory).release(anyString());
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
    when(sessions.session("session-1")).thenReturn(new ActivitySessionClient.SessionInfo("GRID", 19_900, 0, "ONSALE"));
    TicketOrderEntity[] persisted = new TicketOrderEntity[1];
    when(orders.insert(any(TicketOrderEntity.class))).thenAnswer(invocation -> {
      persisted[0] = invocation.getArgument(0);
      persisted[0].setCreatedAt(java.time.Instant.now());
      return 1;
    });
    when(orders.selectById(anyString())).thenAnswer(invocation -> persisted[0]);

    TicketOrderEntity result = repository.create("user-1", "session-1", "A1,A2", "idem-1");

    assertEquals(39_800, result.getAmountMinor());
    assertEquals("A1,A2", result.getSeatIds());
    assertEquals("PENDING", result.getStatus());
    assertEquals(15 * 60, java.time.Duration.between(result.getCreatedAt(), result.getExpireAt()).getSeconds(), 1);
    verify(inventory).reserve(eq(result.getId()), eq("session-1"), eq(List.of("A1", "A2")));
    verify(outbox).write(eq(EventTypes.ORDER_CREATED), eq(result.getId()), any());
  }

  @Test
  void createReservesRedisInventoryBeforeLoadingActivitySession() {
    when(orders.selectOne(any())).thenReturn(null);
    when(sessions.session("session-1"))
        .thenReturn(new ActivitySessionClient.SessionInfo("GRID", 19_900, 0, "ONSALE"));
    TicketOrderEntity[] persisted = new TicketOrderEntity[1];
    when(orders.insert(any(TicketOrderEntity.class))).thenAnswer(invocation -> {
      persisted[0] = invocation.getArgument(0);
      return 1;
    });
    when(orders.selectById(anyString())).thenAnswer(invocation -> persisted[0]);

    repository.create("user-1", "session-1", "A1", "idem-order-first");

    InOrder order = org.mockito.Mockito.inOrder(inventory, sessions);
    order.verify(inventory).reserve(anyString(), eq("session-1"), eq(List.of("A1")));
    order.verify(sessions).session("session-1");
  }

  @Test
  void createSchedulesTimeoutBeforeInsertAndPromotesInventoryAfterInsert() {
    OrderTimeoutZsetService zset = mock(OrderTimeoutZsetService.class);
    OrderRepository zsetRepository = new OrderRepository(orders, inventory, sessions, outbox, zset);
    when(orders.selectOne(any())).thenReturn(null);
    when(sessions.session("session-1"))
        .thenReturn(new ActivitySessionClient.SessionInfo("GRID", 19900, 0, "ONSALE"));
    TicketOrderEntity[] persisted = new TicketOrderEntity[1];
    when(orders.insert(any(TicketOrderEntity.class))).thenAnswer(invocation -> {
      persisted[0] = invocation.getArgument(0);
      persisted[0].setCreatedAt(java.time.Instant.now());
      return 1;
    });
    when(orders.selectById(anyString())).thenAnswer(invocation -> persisted[0]);

    TicketOrderEntity result = zsetRepository.create("user-1", "session-1", "A1", "idem-zset");

    InOrder order = org.mockito.Mockito.inOrder(inventory, zset, orders);
    order.verify(inventory).reserve(anyString(), eq("session-1"), eq(List.of("A1")));
    order.verify(zset).schedule(eq(result.getId()), any(java.time.Instant.class));
    order.verify(orders).insert(any(TicketOrderEntity.class));
    verify(inventory).promote(result.getId());
  }

  @Test
  void createGeneralAdmissionStoresQuantityAndAssignedTicketNumbers() {
    when(orders.selectOne(any())).thenReturn(null);
    when(sessions.session("session-ga")).thenReturn(new ActivitySessionClient.SessionInfo("GENERAL_ADMISSION", 1500, 4));
    when(inventory.reserveQuantity(anyString(), eq("user-1"), eq("session-ga"), eq(2)))
        .thenReturn(List.of(11L, 12L));
    TicketOrderEntity[] persisted = new TicketOrderEntity[1];
    when(orders.insert(any(TicketOrderEntity.class))).thenAnswer(invocation -> { persisted[0] = invocation.getArgument(0); return 1; });
    when(orders.selectById(anyString())).thenAnswer(invocation -> persisted[0]);

    TicketOrderEntity result = repository.createGeneralAdmission("user-1", "session-ga", 2, "idem-ga");

    assertEquals(2, result.getQuantity());
    assertEquals("11,12", result.getTicketNumbers());
    assertEquals(3000, result.getAmountMinor());
    verify(inventory).reserveQuantity(eq(result.getId()), eq("user-1"), eq("session-ga"), eq(2));
  }

  @Test
  void createGeneralAdmissionReleasesReservationWhenIdempotencyRaceIsLost() {
    TicketOrderEntity winner = existing("winner-ga", "user-1", "session-ga", "");
    winner.setQuantity(2);
    when(orders.selectOne(any())).thenReturn(null).thenReturn(winner);
    when(sessions.session("session-ga")).thenReturn(new ActivitySessionClient.SessionInfo("GENERAL_ADMISSION", 1500, 4));
    when(orders.insert(any(TicketOrderEntity.class))).thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate"));

    TicketOrderEntity result = repository.createGeneralAdmission("user-1", "session-ga", 2, "idem-ga");

    assertEquals("winner-ga", result.getId());
    verify(inventory, never()).reserveQuantity(anyString(), anyString(), anyString(), eq(2));
    verify(inventory, never()).releaseQuantity(anyString());
  }

  @Test
  void createGeneralAdmissionClaimsIdempotencyBeforeReservingQuota() {
    TicketOrderEntity winner = existing("winner-ga", "user-1", "session-ga", "");
    winner.setQuantity(2);
    winner.setRequestHash(null);
    when(orders.selectOne(any())).thenReturn(null).thenReturn(winner);
    when(sessions.session("session-ga")).thenReturn(new ActivitySessionClient.SessionInfo("GENERAL_ADMISSION", 1500, 2));
    when(orders.insert(any(TicketOrderEntity.class))).thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate"));

    TicketOrderEntity result = repository.createGeneralAdmission("user-1", "session-ga", 2, "idem-ga");

    assertEquals("winner-ga", result.getId());
    verify(purchases, never()).reserve(anyString(), anyString(), anyString(), eq(2), eq(2), anyLong());
    verify(inventory, never()).reserveQuantity(anyString(), anyString(), anyString(), eq(2));
  }

  @Test
  void createReleasesTheReservationWhenTheInsertLosesTheIdempotencyRace() {
    when(sessions.session("session-1")).thenReturn(new ActivitySessionClient.SessionInfo("GRID", 0, 0, "ONSALE"));
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

  @Test
  void cancelGeneralAdmissionReleasesQuantityAndPurchaseReservation() {
    OrderRepository gaRepository = new OrderRepository(orders, inventory, sessions, outbox, purchases);
    TicketOrderEntity pending = existingWithStatus("order-ga", "PENDING");
    pending.setSeatIds("");
    pending.setQuantity(2);
    when(orders.selectById("order-ga")).thenReturn(pending);
    when(orders.cancelIfCancellable("order-ga")).thenReturn(1);
    TicketOrderEntity cancelled = existingWithStatus("order-ga", "CANCELLED");
    cancelled.setSeatIds("");
    cancelled.setQuantity(2);
    when(orders.selectById("order-ga")).thenReturn(pending, cancelled);

    gaRepository.cancel("order-ga");

    verify(inventory).releaseQuantity("order-ga");
    verify(purchases).release("order-ga", "user-1", "session-1", 2);
    verify(inventory, never()).release("order-ga");
  }

  @Test
  void refundGeneralAdmissionReleasesSoldTicketsAndDecrementsPurchaseCount() {
    OrderRepository gaRepository = new OrderRepository(orders, inventory, sessions, outbox, purchases);
    TicketOrderEntity paid = existingWithStatus("order-ga-refund", "PAID");
    paid.setSeatIds("");
    paid.setQuantity(2);
    when(orders.selectById("order-ga-refund")).thenReturn(paid);
    when(orders.markRefundedIfPaid("order-ga-refund")).thenReturn(1);

    assertEquals(true, gaRepository.markRefunded("order-ga-refund", "refund-1", "reviewer-1"));

    verify(inventory).releaseRefunded("order-ga-refund");
    verify(purchases).decrement("order-ga-refund", "user-1", "session-1", 2);
    verify(inventory, never()).releaseQuantity("order-ga-refund");
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

package com.cloudticket.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.inventory.persistence.AdmissionInventoryRepository;
import com.cloudticket.inventory.persistence.AdmissionTicketRepository;
import com.cloudticket.inventory.persistence.entity.AdmissionInventoryEntity;
import com.cloudticket.inventory.persistence.entity.AdmissionTicketEntity;
import com.cloudticket.inventory.service.AdmissionReservationService;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdmissionReservationServiceTest {

  private final AdmissionInventoryRepository inventory = mock(AdmissionInventoryRepository.class);
  private final AdmissionTicketRepository tickets = mock(AdmissionTicketRepository.class);
  private final AdmissionReservationService service = new AdmissionReservationService(inventory, tickets);

  @Test
  void reservesQuantityWithMonotonicTicketNumbers() {
    when(inventory.lock("session-1")).thenReturn(row(5, 0, 0, 1));

    var reservation = service.reserveQuantity("order-1", "user-1", "session-1", 2, 900);

    assertEquals(List.of(1L, 2L), reservation.ticketNumbers());
    verify(inventory).save(eq("session-1"), eq(5), eq(2), eq(0), eq(3L), eq(2L));
    verify(tickets).insertHeld(eq("order-1"), eq("user-1"), eq("session-1"), eq(List.of(1L, 2L)), any());
  }

  @Test
  void rejectsCapacityOversellBeforeWritingTickets() {
    when(inventory.lock("session-1")).thenReturn(row(2, 1, 1, 4));

    assertThrows(AdmissionReservationService.CapacityExceededException.class,
        () -> service.reserveQuantity("order-1", "user-1", "session-1", 1, 900));
    verify(tickets, never()).insertHeld(any(), any(), any(), any(), any());
  }

  @Test
  void releaseAndConfirmOnlyTransitionHeldTicketsOnce() {
    AdmissionTicketEntity first = ticket(11L);
    AdmissionTicketEntity second = ticket(12L);
    when(tickets.findHeldByOrder("order-1")).thenReturn(List.of(first, second));
    when(tickets.markSold("order-1")).thenReturn(2);
    service.confirm("order-1");
    verify(tickets).markSold("order-1");
    verify(inventory).moveReservedToSold("session-1", 2);
  }

  @Test
  void releaseRefundedSoldTicketsReturnsCapacity() {
    AdmissionTicketEntity sold = ticket(11L);
    sold.setState("SOLD");
    when(tickets.findActiveByOrder("order-1")).thenReturn(List.of(sold));
    when(tickets.markReleasedActive("order-1")).thenReturn(1);

    service.release("order-1");

    verify(tickets).markReleasedActive("order-1");
    verify(inventory).releaseSold("session-1", 1);
  }

  private static AdmissionTicketEntity ticket(long number) {
    AdmissionTicketEntity ticket = new AdmissionTicketEntity();
    ticket.setSessionId("session-1");
    ticket.setTicketNumber(number);
    return ticket;
  }

  private static AdmissionInventoryEntity row(int capacity, int reserved, int sold, long next) {
    AdmissionInventoryEntity row = new AdmissionInventoryEntity();
    row.setSessionId("session-1");
    row.setCapacity(capacity);
    row.setReservedCount(reserved);
    row.setSoldCount(sold);
    row.setNextTicketNumber(next);
    row.setVersion(1L);
    return row;
  }
}

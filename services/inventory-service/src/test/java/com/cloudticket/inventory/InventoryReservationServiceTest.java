package com.cloudticket.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.inventory.persistence.InventoryLockRepository;
import com.cloudticket.inventory.persistence.InventorySeatRepository;
import com.cloudticket.inventory.service.InventoryReservationService;
import com.cloudticket.inventory.redis.RedisSeatLockService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryReservationServiceTest {

  private final InventorySeatRepository seats = mock(InventorySeatRepository.class);
  private final InventoryLockRepository locks = mock(InventoryLockRepository.class);
  private final InventoryReservationService service = new InventoryReservationService(seats, locks);

  @Test
  void rejectsDuplicateSeatIdsBeforeReservation() {
    assertThrows(IllegalArgumentException.class, () ->
        InventoryReservationService.normalizeSeatIds(List.of("seat-1", "seat-1")));
  }

  @Test
  void rejectsAnEmptySelection() {
    assertThrows(IllegalArgumentException.class,
        () -> InventoryReservationService.normalizeSeatIds(List.of()));
  }

  @Test
  void rejectsMoreSeatsThanTheSharedLimit() {
    assertThrows(IllegalArgumentException.class, () -> InventoryReservationService.normalizeSeatIds(
        List.of("s1", "s2", "s3", "s4", "s5", "s6", "s7")));
  }

  @Test
  void lockIsRejectedWhenAnySeatWasTakenInTheMeantime() {
    when(locks.activeSeatIds("order-1")).thenReturn(List.of());
    when(seats.indexes("session-1", List.of("seat-1", "seat-2")))
        .thenReturn(java.util.Map.of("seat-1", 1, "seat-2", 2));
    when(seats.lock("session-1", List.of("seat-1", "seat-2"))).thenReturn(1);

    assertThrows(InventoryReservationService.SeatsUnavailableException.class,
        () -> service.reserve("order-1", "session-1", List.of("seat-1", "seat-2"), 900));
    verify(locks, never()).hold(anyString(), anyString(), anyString(), any());
  }

  @Test
  void reserveHoldsEveryRequestedSeat() {
    when(locks.activeSeatIds("order-1")).thenReturn(List.of());
    when(seats.lock("session-1", List.of("seat-1", "seat-2"))).thenReturn(2);

    var reservation = service.reserve("order-1", "session-1", List.of("seat-1", "seat-2"), 900);

    assertEquals(List.of("seat-1", "seat-2"), reservation.seatIds());
    verify(locks).hold(eq("order-1"), eq("session-1"), eq("seat-1"), any(Instant.class));
    verify(locks).hold(eq("order-1"), eq("session-1"), eq("seat-2"), any(Instant.class));
  }

  @Test
  void reserveCapsTheHoldAtTheMaximumWindow() {
    when(locks.activeSeatIds("order-1")).thenReturn(List.of());
    when(seats.lock("session-1", List.of("seat-1"))).thenReturn(1);

    var reservation = service.reserve("order-1", "session-1", List.of("seat-1"), 86_400);

    assertEquals(true, reservation.expiresAt().isBefore(Instant.now().plusSeconds(1801)));
  }

  @Test
  void reserveIsIdempotentForAnOrderThatAlreadyHoldsSeats() {
    when(locks.activeSeatIds("order-1")).thenReturn(List.of("seat-1"));

    var reservation = service.reserve("order-1", "session-1", List.of("seat-1"), 900);

    assertEquals(List.of("seat-1"), reservation.seatIds());
    verify(seats, never()).lock(anyString(), any());
    verify(locks, never()).hold(anyString(), anyString(), anyString(), any());
  }

  @Test
  void releaseReturnsHeldSeatsAndRetiresTheLocks() {
    when(locks.activeSeatIds("order-1")).thenReturn(List.of("seat-1"));

    service.release("order-1");

    verify(locks).retireActive("order-1", InventoryLockRepository.RELEASED);
    verify(seats).release(List.of("seat-1"));
  }

  @Test
  void confirmSellsHeldSeatsAndRetiresTheLocks() {
    when(locks.activeSeatIds("order-1")).thenReturn(List.of("seat-1", "seat-2"));
    when(seats.sell(List.of("seat-1", "seat-2"))).thenReturn(2);

    assertEquals(2, service.confirm("order-1"));
    verify(locks).retireActive("order-1", InventoryLockRepository.CONFIRMED);
  }

  @Test
  void confirmIsANoOpWithoutActiveLocks() {
    when(locks.activeSeatIds("order-1")).thenReturn(List.of());

    assertEquals(0, service.confirm("order-1"));
    verify(seats, never()).sell(any());
    verify(locks, never()).retireActive(anyString(), anyString());
  }

  @Test
  void expiryScanReleasesEveryExpiredOrder() {
    when(locks.expiredOrderIds()).thenReturn(List.of("order-1", "order-2"));
    when(locks.activeSeatIds("order-1")).thenReturn(List.of("seat-1"));
    when(locks.activeSeatIds("order-2")).thenReturn(List.of("seat-2"));

    service.expireReservations();

    verify(seats).release(List.of("seat-1"));
    verify(seats).release(List.of("seat-2"));
  }

  @Test
  void redisHoldIsCompensatedWhenMysqlCannotLockEverySeat() {
    RedisSeatLockService redisLocks = mock(RedisSeatLockService.class);
    when(redisLocks.isEnabled()).thenReturn(true);
    when(locks.activeSeatIds("order-1")).thenReturn(List.of());
    when(seats.indexes("session-1", List.of("seat-1", "seat-2")))
        .thenReturn(java.util.Map.of("seat-1", 1, "seat-2", 2));
    when(redisLocks.reserveIndexed("order-1", "session-1", List.of(1, 2), 900)).thenReturn(true);
    when(seats.lock("session-1", List.of("seat-1", "seat-2"))).thenReturn(1);
    var redisService = new InventoryReservationService(seats, locks, null, redisLocks);

    assertThrows(InventoryReservationService.SeatsUnavailableException.class,
        () -> redisService.reserve("order-1", "session-1", List.of("seat-1", "seat-2"), 900));
    verify(redisLocks).releaseIndexed("order-1", "session-1", List.of(1, 2));
    verify(locks, never()).hold(anyString(), anyString(), anyString(), any());
  }

  @Test
  void redisConflictRejectsBeforeTouchingMysql() {
    RedisSeatLockService redisLocks = mock(RedisSeatLockService.class);
    when(redisLocks.isEnabled()).thenReturn(true);
    when(locks.activeSeatIds("order-1")).thenReturn(List.of());
    when(seats.indexes("session-1", List.of("seat-1")))
        .thenReturn(java.util.Map.of("seat-1", 1));
    when(redisLocks.reserveIndexed("order-1", "session-1", List.of(1), 900)).thenReturn(false);
    var redisService = new InventoryReservationService(seats, locks, null, redisLocks);

    assertThrows(InventoryReservationService.SeatsUnavailableException.class,
        () -> redisService.reserve("order-1", "session-1", List.of("seat-1"), 900));
    verify(seats, never()).lock(anyString(), any());
  }
}

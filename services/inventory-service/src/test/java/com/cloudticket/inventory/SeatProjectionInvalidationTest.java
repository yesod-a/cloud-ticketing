package com.cloudticket.inventory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.inventory.cache.SeatBitmapProjection;
import com.cloudticket.inventory.cache.InventoryLayoutProjection;
import com.cloudticket.inventory.persistence.InventoryLockRepository;
import com.cloudticket.inventory.persistence.InventorySeatRepository;
import com.cloudticket.inventory.service.InventoryReservationService;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeatProjectionInvalidationTest {

  private final InventorySeatRepository seats = mock(InventorySeatRepository.class);
  private final InventoryLockRepository locks = mock(InventoryLockRepository.class);
  private final SeatBitmapProjection projection = mock(SeatBitmapProjection.class);
  private final InventoryLayoutProjection layoutProjection = mock(InventoryLayoutProjection.class);
  private final InventoryReservationService service =
      new InventoryReservationService(seats, locks, projection, null, layoutProjection);

  @Test
  void reserveInvalidatesSessionAfterDurableHold() {
    when(locks.activeSeatIds("order-1")).thenReturn(List.of());
    when(seats.lock("session-1", List.of("seat-1"))).thenReturn(1);

    service.reserve("order-1", "session-1", List.of("seat-1"), 60);

    verify(projection).invalidate("session-1");
    verify(layoutProjection).invalidate("session-1");
  }

  @Test
  void releaseInvalidatesEveryAffectedSession() {
    when(locks.activeSeatIds("order-1")).thenReturn(List.of("seat-1"));
    when(locks.activeSessionIds("order-1")).thenReturn(List.of("session-1"));

    service.release("order-1");

    verify(projection).invalidate("session-1");
    verify(layoutProjection).invalidate("session-1");
  }

  @Test
  void confirmInvalidatesEveryAffectedSession() {
    when(locks.activeSeatIds("order-1")).thenReturn(List.of("seat-1"));
    when(locks.activeSessionIds("order-1")).thenReturn(List.of("session-1"));
    when(seats.sell(List.of("seat-1"))).thenReturn(1);

    service.confirm("order-1");

    verify(projection).invalidate("session-1");
    verify(layoutProjection).invalidate("session-1");
  }
}

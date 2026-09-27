package com.cloudticket.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.cloudticket.inventory.cache.SeatBitmapProjection;
import com.cloudticket.inventory.persistence.entity.InventorySeatEntity;
import com.cloudticket.inventory.reconcile.SeatProjectionReconciler;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SeatProjectionReconcilerTest {

  private final SeatBitmapProjection projection = mock(SeatBitmapProjection.class);
  private final SeatProjectionReconciler reconciler = new SeatProjectionReconciler(projection);

  @Test
  void rebuildsMissingBitmapAndReturnsSafeOrphans() {
    var seats = List.of(new InventorySeatEntity());

    var report = reconciler.reconcile("session-1", seats, Set.of("seat-1"),
        Set.of("seat-1", "seat-2"), Set.of(), false);

    verify(projection).rebuild("session-1", seats);
    assertEquals(Set.of("seat-2"), report.safeOrphanHolds());
    assertEquals(Set.of(), report.ambiguousPaidLockedSeats());
    assertEquals(true, report.rebuiltBitmap());
  }

  @Test
  void neverMarksPaidLockedOrphanAsSafeToDelete() {
    var report = reconciler.reconcile("session-1", List.of(), Set.of(),
        Set.of("seat-2"), Set.of("seat-2"), true);

    assertEquals(Set.of(), report.safeOrphanHolds());
    assertEquals(Set.of("seat-2"), report.ambiguousPaidLockedSeats());
  }
}

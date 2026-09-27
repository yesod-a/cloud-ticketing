package com.cloudticket.inventory.reconcile;

import com.cloudticket.inventory.cache.SeatBitmapProjection;
import com.cloudticket.inventory.persistence.entity.InventorySeatEntity;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Bounded reconciliation decisions for the Redis seat projection.
 *
 * <p>The caller supplies snapshots from MySQL and Redis so the scan can be run from a scheduled
 * job or an operator tool without making Redis the source of truth. Ambiguous paid/locked states
 * are reported, never silently repaired.
 */
@Component
public class SeatProjectionReconciler {

  private final SeatBitmapProjection projection;

  public SeatProjectionReconciler(SeatBitmapProjection projection) {
    this.projection = projection;
  }

  public ReconciliationReport reconcile(String sessionId, List<InventorySeatEntity> durableSeats,
                                        Set<String> durableHeldSeatIds, Set<String> redisHeldSeatIds,
                                        Set<String> paidLockedSeatIds, boolean bitmapReady) {
    if (!bitmapReady) projection.rebuild(sessionId, durableSeats);

    Set<String> durable = copy(durableHeldSeatIds);
    Set<String> redis = copy(redisHeldSeatIds);
    Set<String> paidLocked = copy(paidLockedSeatIds);

    Set<String> orphanHolds = new HashSet<>(redis);
    orphanHolds.removeAll(durable);
    Set<String> safeToDelete = new HashSet<>(orphanHolds);
    safeToDelete.removeAll(paidLocked);
    Set<String> ambiguous = new HashSet<>(paidLocked);
    ambiguous.retainAll(redis);
    ambiguous.removeAll(durable);

    return new ReconciliationReport(!bitmapReady, Set.copyOf(safeToDelete), Set.copyOf(ambiguous));
  }

  private static Set<String> copy(Set<String> values) {
    return values == null ? new HashSet<>() : new HashSet<>(values);
  }

  public record ReconciliationReport(boolean rebuiltBitmap, Set<String> safeOrphanHolds,
                                     Set<String> ambiguousPaidLockedSeats) {}
}

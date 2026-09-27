package com.cloudticket.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.common.security.AuditEntry;
import com.cloudticket.common.security.AuditSink;
import com.cloudticket.common.security.CallerContext;
import com.cloudticket.common.security.CallerContextHolder;
import com.cloudticket.inventory.api.InventoryCommands;
import com.cloudticket.inventory.api.InventorySeatLookup;
import com.cloudticket.inventory.api.InternalInventoryController;
import com.cloudticket.inventory.persistence.InventorySeatRepository;
import com.cloudticket.inventory.persistence.entity.InventorySeatEntity;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class InternalInventoryControllerTest {

  private final InventorySeatRepository seats = mock(InventorySeatRepository.class);
  private final AuditSink auditSink = mock(AuditSink.class);

  private InternalInventoryController controller() {
    return TestAspects.authorized(new InternalInventoryController(seats), auditSink,
        Map.of("inventorySeats", new InventorySeatLookup(seats)));
  }

  @Test
  void adjustRequiresTheInternalServiceToken() {
    assertThrows(SecurityException.class, () -> asCaller("inventory:adjust", "SESSION:session-1", "",
        () -> controller().adjust("seat-1", new InventoryCommands.AdjustSeat("LOCKED", "repair"))));
    verify(seats, never()).adjust(anyString(), anyString());
  }

  @Test
  void adjustRequiresAReason() {
    seat("seat-1", "session-1", "activity-1", "AVAILABLE");

    assertThrows(SecurityException.class, () -> asCaller("inventory:adjust", "SESSION:session-1",
        "dev-internal-token",
        () -> controller().adjust("seat-1", new InventoryCommands.AdjustSeat("LOCKED", "  "))));
    verify(seats, never()).adjust(anyString(), anyString());
  }

  @Test
  void adjustCannotCrossSessionScope() {
    seat("seat-1", "session-2", "activity-2", "AVAILABLE");

    assertThrows(SecurityException.class, () -> asCaller("inventory:adjust", "SESSION:session-1",
        "dev-internal-token",
        () -> controller().adjust("seat-1", new InventoryCommands.AdjustSeat("LOCKED", "repair"))));
    verify(seats, never()).adjust(anyString(), anyString());
    verify(auditSink, never()).record(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void adjustAuditsBeforeAndAfterWithTheOperatorReason() {
    seat("seat-1", "session-1", "activity-1", "AVAILABLE");

    Map<String, Object> response = asCaller("inventory:adjust", "SESSION:session-1", "dev-internal-token",
        () -> controller().adjust("seat-1", new InventoryCommands.AdjustSeat("DISABLED", "seat repair")));

    assertEquals("DISABLED", response.get("status"));
    verify(seats).adjust("seat-1", "DISABLED");
    verify(auditSink).record(new AuditEntry("operator-1", "SEAT_STATUS_ADJUSTED", "INVENTORY_SEAT", "seat-1",
        "AVAILABLE", "DISABLED", "trace-1", "seat repair"));
  }

  @Test
  void adjustRejectsAnUnknownStatus() {
    seat("seat-1", "session-1", "activity-1", "AVAILABLE");

    assertThrows(IllegalArgumentException.class, () -> asCaller("inventory:adjust", "SESSION:session-1",
        "dev-internal-token",
        () -> controller().adjust("seat-1", new InventoryCommands.AdjustSeat("BROKEN", "repair"))));
  }

  @Test
  void lockReleaseIsAuditedAsAvailable() {
    seat("seat-1", "session-1", "activity-1", "LOCKED");

    Map<String, Object> response = asCaller("inventory:lock-release", "ACTIVITY:activity-1",
        "dev-internal-token",
        () -> controller().release("seat-1", new InventoryCommands.AdjustSeat(null, "customer cancelled")));

    assertEquals("AVAILABLE", response.get("status"));
    verify(auditSink).record(new AuditEntry("operator-1", "SEAT_LOCK_RELEASED", "INVENTORY_SEAT", "seat-1",
        "LOCKED", "AVAILABLE", "trace-1", "customer cancelled"));
  }

  @Test
  void listingRequiresTheInventoryReadPermission() {
    assertThrows(SecurityException.class, () -> asCaller("order:read", "", "dev-internal-token",
        () -> controller().list("", "", 0, 20)));
    verify(seats, never()).page(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt(),
        org.mockito.ArgumentMatchers.anyInt());
  }

  private void seat(String id, String sessionId, String activityId, String status) {
    InventorySeatEntity entity = InventorySeatRepository.seat(id, sessionId, activityId, "", "A", 1, "A-1",
        "REGULAR", null, null, status);
    when(seats.find(id)).thenReturn(Optional.of(entity));
  }

  private static <T> T asCaller(String permissions, String scopes, String internalToken,
                                Supplier<T> action) {
    return CallerContextHolder.scoped(
        new CallerContext(permissions, scopes, "operator-1", "trace-1", internalToken), action);
  }
}

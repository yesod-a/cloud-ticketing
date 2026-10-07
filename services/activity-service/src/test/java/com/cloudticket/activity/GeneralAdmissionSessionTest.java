package com.cloudticket.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloudticket.activity.api.command.SessionCommands;
import com.cloudticket.activity.domain.Session;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class GeneralAdmissionSessionTest {
  @Test
  void sessionCommandDefaultsToGridAndUnlimitedPurchaseLimit() {
    var command = new SessionCommands.CreateSession("venue-1", "2026-10-01T10:00:00Z",
        "2026-10-01T11:00:00Z", null, null);
    assertEquals("GRID", command.layoutModeOrDefault());
    assertEquals(0, command.capacityOrZero());
    assertEquals(0, command.purchaseLimitOrZero());
  }

  @Test
  void generalAdmissionSessionCarriesCapacityAndPurchaseLimit() {
    Session session = Session.from("s", "a", Instant.parse("2026-10-01T10:00:00Z"),
        Instant.parse("2026-10-01T11:00:00Z"), "Hall", "DRAFT", 1000,
        "GENERAL_ADMISSION", 500, 4);
    assertEquals("GENERAL_ADMISSION", session.layoutMode());
    assertEquals(500, session.capacity());
    assertEquals(4, session.purchaseLimit());
  }

  @Test
  void generalAdmissionCommandRejectsNegativeValuesAtBoundary() {
    assertThrows(IllegalArgumentException.class, () -> new com.cloudticket.activity.domain.SeatLayoutRules(
        "GENERAL_ADMISSION", "", null, null, null, null, java.util.List.of(), -1));
  }
}

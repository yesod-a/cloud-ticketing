package com.cloudticket.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cloudticket.activity.api.command.SessionCommands;
import com.cloudticket.activity.domain.Session;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SessionSaleStartTest {

  @Test
  void sessionCarriesExplicitSaleStartTime() {
    SessionCommands.CreateSession command = new SessionCommands.CreateSession(
        "venue-1", "2026-12-01T12:00:00Z", "2026-12-01T15:00:00Z", "DRAFT", 1000,
        "GRID", null, null, "DIRECT", "2026-11-01T12:00:00Z");

    Session session = Session.from("session-1", "activity-1", Instant.parse(command.startsAt()),
        Instant.parse(command.endsAt()), "Venue", command.statusOrDefault(), command.priceOrZero(),
        command.layoutModeOrDefault(), command.capacityOrZero(), command.purchaseLimitOrZero(),
        command.saleModeOrDefault(), command.saleStartAt());

    assertEquals("2026-11-01T12:00:00Z", session.saleStartAt());
  }
}

package com.cloudticket.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cloudticket.activity.service.ActivityCatalog;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class VenueSeatTemplateTest {
  @Test
  void createVenueSeatPersistsTemplateFieldsWithDefaultStatus() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ActivityCatalog catalog = new ActivityCatalog(jdbc);

    var created = catalog.createVenueSeat("venue-1", "VIP", "A", 7, "VIP-A-7", 6, 0, "VIP", true);

    verify(jdbc).update(contains("INSERT INTO venue_seat"), anyString(), eq("venue-1"),
        eq("VIP"), eq("A"), eq(7), eq("VIP-A-7"), eq(6), eq(0), eq("VIP"), eq(true), eq("AVAILABLE"));
    assertEquals("AVAILABLE", created.status());
  }

  @Test
  void gridLayoutGeneratesExpectedSeatCountAndCoordinates() {
    var catalog = new ActivityCatalog();
    var seats = catalog.generateVenueLayout("venue-1", "GRID",
        Map.<String,Object>of("areaLabel", "看台", "rowCount", 2, "seatsPerRow", 3, "rowLabelType", "LETTER", "startSeatNumber", 1));
    assertEquals(6, seats.size());
    assertEquals("A-1", seats.get(0).displayName());
    assertEquals("看台", seats.get(0).areaLabel());
    assertEquals(0, seats.get(0).y());
    assertEquals(1, seats.get(3).y());
  }

  @Test
  void rowsLayoutHonorsPerRowCounts() {
    var catalog = new ActivityCatalog();
    var seats = catalog.generateVenueLayout("venue-1", "ROWS",
        Map.<String,Object>of("areaLabel", "VIP", "rows",
            List.of(Map.of("rowLabel", "A", "seatCount", 2), Map.of("rowLabel", "B", "seatCount", 3))));
    assertEquals(5, seats.size());
    assertEquals("A", seats.get(0).rowLabel());
    assertEquals("B", seats.get(2).rowLabel());
  }

  @Test
  void createSessionRejectsNonIncreasingTimeWindowBeforeDatabaseWrites() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ActivityCatalog catalog = new ActivityCatalog(jdbc);

    assertThrows(IllegalArgumentException.class,
        () -> catalog.createSession("activity-1", "venue-1",
            "2026-10-01T10:00:00Z", "2026-10-01T10:00:00Z", "DRAFT"));
    verifyNoInteractions(jdbc);
  }

  @Test
  void createSessionCopiesVenueTemplateIntoIndependentSessionSeats() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ActivityCatalog catalog = new ActivityCatalog(jdbc);
    when(jdbc.query(contains("SELECT s.id,s.activity_id"),
        org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<ActivityCatalog.Session>>any(),
        anyString())).thenReturn(List.of(
        new ActivityCatalog.Session("session-1", "activity-1", "2026-10-01T10:00:00Z",
            "2026-10-01T11:00:00Z", "Hall", "DRAFT")));

    catalog.createSession("activity-1", "venue-1", "2026-10-01T10:00:00Z",
        "2026-10-01T11:00:00Z", "DRAFT");

    verify(jdbc).update(contains("INSERT INTO seat"), anyString(), eq("venue-1"));
  }
}

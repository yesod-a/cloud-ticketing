package com.cloudticket.activity;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cloudticket.activity.service.ActivityCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class VenueSeatTemplateTest {
  @Test
  void createVenueSeatPersistsTemplateFieldsWithDefaultStatus() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ActivityCatalog catalog = new ActivityCatalog(jdbc);

    var created = catalog.createVenueSeat("venue-1", "A", 7, "120,80");

    verify(jdbc).update(contains("INSERT INTO venue_seat"), anyString(), eq("venue-1"),
        eq("A"), eq(7), eq("120,80"), eq("AVAILABLE"));
    org.junit.jupiter.api.Assertions.assertEquals("AVAILABLE", created.status());
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
    when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM venue"), eq(Integer.class),
        eq("venue-1"), eq("activity-1"))).thenReturn(1);
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

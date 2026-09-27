package com.cloudticket.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.activity.api.VenueAdminController;
import com.cloudticket.activity.api.command.VenueCommands;
import com.cloudticket.activity.domain.SeatLayoutRules;
import com.cloudticket.activity.domain.Venue;
import com.cloudticket.activity.domain.VenueSeat;
import com.cloudticket.activity.api.ActivitySnapshots;
import com.cloudticket.activity.service.ActivityService;
import com.cloudticket.activity.service.VenueService;
import com.cloudticket.common.security.AuditEntry;
import com.cloudticket.common.security.AuditSink;
import com.cloudticket.common.security.CallerContext;
import com.cloudticket.common.security.CallerContextHolder;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class VenueAdminControllerTest {

  private final VenueService venues = mock(VenueService.class);
  private final ActivityService activities = mock(ActivityService.class);
  private final AuditSink auditSink = mock(AuditSink.class);

  private VenueAdminController controller() {
    return TestAspects.authorized(new VenueAdminController(venues), auditSink,
        Map.of("activitySnapshots", new ActivitySnapshots(activities, venues)));
  }

  @Test
  void venueCreationAuditsTheCreatedVenueFromTheResponseEnvelope() {
    Venue created = new Venue("venue-1", "activity-1", "Hall", "Address");
    when(venues.create("activity-1", "Hall", "Address")).thenReturn(created);

    asCaller("venue:write", () -> controller().create(
        new VenueCommands.CreateVenue("activity-1", "Hall", "Address")));

    verify(auditSink).record(new AuditEntry("actor-1", "VENUE_CREATED", "VENUE", "venue-1",
        null, created.toString(), "trace-1"));
  }

  @Test
  void venueUpdateCapturesThePreviousState() {
    Venue before = new Venue("venue-1", "activity-1", "Old", "Address");
    Venue after = new Venue("venue-1", "activity-1", "New", "Address");
    when(venues.find("venue-1")).thenReturn(java.util.Optional.of(before));
    when(venues.update("venue-1", "New", "Address")).thenReturn(after);

    asCaller("venue:write", () -> controller().update("venue-1", new VenueCommands.UpdateVenue("New", "Address")));

    verify(auditSink).record(new AuditEntry("actor-1", "VENUE_UPDATED", "VENUE", "venue-1",
        before.toString(), after.toString(), "trace-1"));
  }

  @Test
  void layoutGenerationRecordsHowManySeatsWereCreated() {
    List<VenueSeat> generated = List.of(
        new VenueSeat("s1", "venue-1", "", "A", 1, "A-1", 0, 0, "REGULAR", true, "AVAILABLE"),
        new VenueSeat("s2", "venue-1", "", "A", 2, "A-2", 1, 0, "REGULAR", true, "AVAILABLE"));
    when(venues.generateLayout(anyString(), any())).thenReturn(generated);

    asCaller("seat-layout:write", () -> controller().layout("venue-1",
        new SeatLayoutRules("GRID", "", 1, 2, "LETTER", 1, List.of())));

    verify(auditSink).record(new AuditEntry("actor-1", "VENUE_LAYOUT_GENERATED", "VENUE", "venue-1",
        null, "2 seats", "trace-1"));
  }

  @Test
  void seatCreationRequiresTheLayoutPermission() {
    assertThrows(SecurityException.class, () -> asCaller("venue:read", () -> controller().createSeat("venue-1",
        new VenueCommands.CreateVenueSeat("", "A", 1, null, null, null, null, null))));
    verify(venues, never()).createSeat(anyString(), anyString(), anyString(), anyInt(), anyString(),
        anyInt(), anyInt(), anyString(), org.mockito.ArgumentMatchers.anyBoolean());
  }

  @Test
  void seatCreationAppliesCommandDefaults() {
    VenueSeat created = new VenueSeat("s1", "venue-1", "", "A", 1, "A-1", 0, 0, "REGULAR", true, "AVAILABLE");
    when(venues.createSeat("venue-1", "", "A", 1, null, 0, 0, null, true)).thenReturn(created);

    asCaller("seat-layout:write", () -> controller().createSeat("venue-1",
        new VenueCommands.CreateVenueSeat("", "A", 1, null, null, null, null, null)));

    assertEquals("s1", created.id());
  }

  private static <T> T asCaller(String permissions, Supplier<T> action) {
    return CallerContextHolder.scoped(
        new CallerContext(permissions, "", "actor-1", "trace-1", ""), action);
  }
}

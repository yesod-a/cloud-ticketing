package com.cloudticket.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cloudticket.activity.api.ActivityAdminController;
import com.cloudticket.activity.api.VenueAdminController;
import com.cloudticket.activity.service.ActivityCatalog;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActivityAuditControllerTest {
  private final ActivityCatalog catalog = mock(ActivityCatalog.class);
  private final ActivityAdminController controller = new ActivityAdminController(catalog);

  @Test
  void activityMutationsWriteActorTraceAndBeforeAfterAudit() {
    var activity = new ActivityCatalog.Activity("activity-1", "A", "Org", "DRAFT", false);
    var session = new ActivityCatalog.Session("session-1", "activity-1", "2026-10-01T10:00:00Z", "2026-10-01T11:00:00Z", "Venue", "DRAFT");
    var seat = new ActivityCatalog.Seat("seat-1", "A", 1, "AVAILABLE");
    when(catalog.create(anyString(), nullable(String.class))).thenReturn(activity);
    when(catalog.createSession(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt())).thenReturn(session);
    when(catalog.publish(anyString())).thenReturn(new ActivityCatalog.Activity("activity-1", "A", "Org", "PUBLISHED", false));
    when(catalog.offline(anyString())).thenReturn(new ActivityCatalog.Activity("activity-1", "A", "Org", "OFFLINE", false));
    when(catalog.freezeLayout(anyString())).thenReturn(new ActivityCatalog.Activity("activity-1", "A", "Org", "PUBLISHED", true));
    when(catalog.get("activity-1")).thenReturn(activity);
    when(catalog.updateSeat(anyString(), anyString())).thenReturn(seat);
    when(catalog.getSeat("seat-1")).thenReturn(new ActivityCatalog.Seat("seat-1", "A", 1, "AVAILABLE"));

    controller.create(Map.of("title", "A"), "activity:write", "actor-1", "trace-1");
    controller.createSession("activity-1", Map.of("venueId", "venue-1", "startsAt", "2026-10-01T10:00:00Z", "endsAt", "2026-10-01T11:00:00Z"), "session:write", "ACTIVITY:activity-1", "actor-1", "trace-2");
    controller.publish("activity-1", "activity:publish", "ACTIVITY:activity-1", "actor-1", "trace-3");
    controller.offline("activity-1", "activity:publish", "ACTIVITY:activity-1", "actor-1", "trace-4");
    controller.freeze("activity-1", "seat-layout:write", "ACTIVITY:activity-1", "actor-1", "trace-5");
    when(catalog.activityIdForSeat("seat-1")).thenReturn("activity-1");
    controller.updateSeat("seat-1", Map.of("status", "SOLD"), "seat-layout:write", "ACTIVITY:activity-1", "actor-1", "trace-6");

    verify(catalog).audit(eq("actor-1"), eq("ACTIVITY_CREATED"), eq("ACTIVITY"), eq("activity-1"), isNull(), anyString(), eq("trace-1"));
    verify(catalog).audit(eq("actor-1"), eq("SESSION_CREATED"), eq("SESSION"), eq("session-1"), isNull(), anyString(), eq("trace-2"));
    verify(catalog).audit(eq("actor-1"), eq("ACTIVITY_PUBLISHED"), eq("ACTIVITY"), eq("activity-1"), eq(activity.toString()), anyString(), eq("trace-3"));
    verify(catalog).audit(eq("actor-1"), eq("ACTIVITY_OFFLINED"), eq("ACTIVITY"), eq("activity-1"), eq(activity.toString()), anyString(), eq("trace-4"));
    verify(catalog).audit(eq("actor-1"), eq("LAYOUT_FROZEN"), eq("ACTIVITY"), eq("activity-1"), eq(activity.toString()), anyString(), eq("trace-5"));
    verify(catalog).audit(eq("actor-1"), eq("SEAT_UPDATED"), eq("SEAT"), eq("seat-1"), eq("Seat[id=seat-1, row=A, number=1, status=AVAILABLE]"), anyString(), eq("trace-6"));
  }

  @Test
  void venueCreationWritesActorTraceAndAudit() {
    var venue = new ActivityCatalog.Venue("venue-1", "activity-1", "Hall", "Address");
    when(catalog.createVenue("activity-1", "Hall", "Address")).thenReturn(venue);
    var controller = new VenueAdminController(catalog);

    controller.create(Map.of("activityId", "activity-1", "name", "Hall", "address", "Address"),
        "activity:write", "actor-1", "trace-7");

    verify(catalog).audit(eq("actor-1"), eq("VENUE_CREATED"), eq("VENUE"), eq("venue-1"),
        isNull(), anyString(), eq("trace-7"));
  }

  @Test
  void activityUpdateWritesAudit() {
    var before = new ActivityCatalog.Activity("activity-1", "Old", "Org", "DRAFT", false);
    var after = new ActivityCatalog.Activity("activity-1", "New", "Org2", "DRAFT", false);
    when(catalog.get("activity-1")).thenReturn(before);
    when(catalog.update("activity-1", "New", "Org2")).thenReturn(after);
    controller.update("activity-1", Map.of("title", "New", "organizer", "Org2"), "activity:write", "ACTIVITY:activity-1", "actor-1", "trace-8");
    verify(catalog).audit(eq("actor-1"), eq("ACTIVITY_UPDATED"), eq("ACTIVITY"), eq("activity-1"), eq(before.toString()), eq(after.toString()), eq("trace-8"));
  }

  @Test
  void scopedOperatorCannotMutateAnotherActivity() {
    assertThrows(SecurityException.class, () -> controller.update(
        "activity-2", Map.of("title", "New"), "activity:write", "ACTIVITY:activity-1", "actor-1", "trace-9"));
    assertThrows(SecurityException.class, () -> controller.publish(
        "activity-2", "activity:publish", "ACTIVITY:activity-1", "actor-1", "trace-10"));
  }

  @Test
  void readOnlyOperatorCannotCreateActivity() {
    assertThrows(SecurityException.class, () -> controller.create(
        Map.of("title", "A"), "activity:read", "actor-1", "trace-11"));
    verifyNoInteractions(catalog);
  }
}

package com.cloudticket.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cloudticket.activity.api.ActivityAdminController;
import com.cloudticket.activity.api.ActivitySnapshots;
import com.cloudticket.activity.api.command.ActivityCommands;
import com.cloudticket.activity.api.command.SessionCommands;
import com.cloudticket.activity.domain.Activity;
import com.cloudticket.activity.domain.Seat;
import com.cloudticket.activity.domain.Session;
import com.cloudticket.activity.service.ActivityService;
import com.cloudticket.activity.service.SessionService;
import com.cloudticket.activity.service.VenueService;
import com.cloudticket.common.security.AuditEntry;
import com.cloudticket.common.security.AuditSink;
import com.cloudticket.common.security.CallerContext;
import com.cloudticket.common.security.CallerContextHolder;
import com.cloudticket.common.web.PageResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ActivityAdminControllerTest {

  private static final String WINDOW_START = "2026-10-01T10:00:00Z";
  private static final String WINDOW_END = "2026-10-01T11:00:00Z";

  private final ActivityService activities = mock(ActivityService.class);
  private final SessionService sessions = mock(SessionService.class);
  private final VenueService venues = mock(VenueService.class);
  private final AuditSink auditSink = mock(AuditSink.class);
  private final ActivitySnapshots snapshots = new ActivitySnapshots(activities, venues);

  private ActivityAdminController controller() {
    return TestAspects.authorized(new ActivityAdminController(activities, sessions), auditSink,
        Map.of("activitySnapshots", snapshots));
  }

  @Test
  void activityCreateWritesActorTraceAndCreatedResource() {
    Activity created = new Activity("activity-1", "A", "Org", "OFFLINE", false);
    when(activities.create("A", "Org")).thenReturn(created);

    Activity response = asCaller("activity:write", "",
        () -> controller().create(new ActivityCommands.CreateActivity("A", "Org")));

    assertEquals("activity-1", response.id());
    verify(auditSink).record(new AuditEntry("actor-1", "ACTIVITY_CREATED", "ACTIVITY", "activity-1",
        null, created.toString(), "trace-1"));
  }

  @Test
  void activityUpdateWritesBeforeAndAfterSnapshots() {
    Activity before = new Activity("activity-1", "Old", "Org", "DRAFT", false);
    Activity after = new Activity("activity-1", "New", "Org2", "DRAFT", false);
    when(activities.find("activity-1")).thenReturn(Optional.of(before));
    when(activities.update("activity-1", "New", "Org2")).thenReturn(after);

    asCaller("activity:write", "ACTIVITY:activity-1",
        () -> controller().update("activity-1", new ActivityCommands.UpdateActivity("New", "Org2")));

    verify(auditSink).record(new AuditEntry("actor-1", "ACTIVITY_UPDATED", "ACTIVITY", "activity-1",
        before.toString(), after.toString(), "trace-1"));
  }

  @Test
  void sessionCreateAuditsTheSessionAndAppliesCommandDefaults() {
    Session created = new Session("session-1", "activity-1", WINDOW_START, WINDOW_END, "Hall", "DRAFT", 0);
    when(sessions.create("activity-1", "venue-1", WINDOW_START, WINDOW_END, "DRAFT", 0)).thenReturn(created);

    asCaller("session:write", "ACTIVITY:activity-1", () -> controller().createSession("activity-1",
        new SessionCommands.CreateSession("venue-1", WINDOW_START, WINDOW_END, null, null)));

    verify(auditSink).record(new AuditEntry("actor-1", "SESSION_CREATED", "SESSION", "session-1",
        null, created.toString(), "trace-1"));
  }

  @Test
  void deleteActionsDoNotRecordASnapshot() {
    asCaller("session:write", "ACTIVITY:activity-1",
        () -> controller().deleteSession("activity-1", "session-1"));

    verify(sessions).delete("session-1");
    verify(auditSink).record(new AuditEntry("actor-1", "SESSION_DELETED", "SESSION", "session-1",
        null, null, "trace-1"));
  }

  @Test
  void scopedOperatorCannotMutateAnotherActivity() {
    assertThrows(SecurityException.class, () -> asCaller("activity:write", "ACTIVITY:activity-1",
        () -> controller().publish("activity-2")));
    verifyNoInteractions(activities);
    verifyNoInteractions(auditSink);
  }

  @Test
  void readOnlyOperatorCannotCreateActivity() {
    assertThrows(SecurityException.class, () -> asCaller("activity:read", "",
        () -> controller().create(new ActivityCommands.CreateActivity("A", "Org"))));
    verifyNoInteractions(activities);
  }

  @Test
  void seatUpdateResolvesTheOwningActivityForTheScopeCheck() {
    when(activities.activityIdForSeat("seat-1")).thenReturn("activity-2");

    assertThrows(SecurityException.class, () -> asCaller("seat-layout:write", "ACTIVITY:activity-1",
        () -> controller().updateSeat("seat-1", new SessionCommands.UpdateSeat("SOLD"))));
    verify(sessions, never()).updateSeat(anyString(), anyString());
  }

  @Test
  void seatUpdateAuditsThePreviousSeatState() {
    Seat before = new Seat("seat-1", "A", 1, "AVAILABLE");
    Seat after = new Seat("seat-1", "A", 1, "SOLD");
    when(activities.activityIdForSeat("seat-1")).thenReturn("activity-1");
    when(activities.seatSnapshot("seat-1")).thenReturn(before.toString());
    when(sessions.updateSeat("seat-1", "SOLD")).thenReturn(after);

    asCaller("seat-layout:write", "ACTIVITY:activity-1",
        () -> controller().updateSeat("seat-1", new SessionCommands.UpdateSeat("SOLD")));

    verify(auditSink).record(new AuditEntry("actor-1", "SEAT_UPDATED", "SEAT", "seat-1",
        before.toString(), after.toString(), "trace-1"));
  }

  @Test
  void systemConfigBypassesPermissionAndScope() {
    when(activities.publish("activity-2"))
        .thenReturn(new Activity("activity-2", "B", "Org", "PUBLISHED", false));

    asCaller("system:config", "", () -> controller().publish("activity-2"));

    verify(activities).publish("activity-2");
  }

  @Test
  void adminListingPassesTheCallerScopeToTheService() {
    when(activities.adminActivities("", "PUBLISHED", 0, 10, "activity:read", "ACTIVITY:activity-1"))
        .thenReturn(new PageResult<>(List.of(), 0, 10, 0));

    Map<String, Object> response = asCaller("activity:read", "ACTIVITY:activity-1",
        () -> controller().list("", "PUBLISHED", 0, 10));

    assertEquals(0L, response.get("total"));
  }

  private static <T> T asCaller(String permissions, String scopes, Supplier<T> action) {
    return CallerContextHolder.scoped(
        new CallerContext(permissions, scopes, "actor-1", "trace-1", ""), action);
  }
}

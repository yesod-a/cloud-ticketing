package com.cloudticket.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.activity.persistence.ActivityRepository;
import com.cloudticket.activity.persistence.ActivityImageRepository;
import com.cloudticket.activity.persistence.AuditRepository;
import com.cloudticket.activity.persistence.SessionRepository;
import com.cloudticket.activity.persistence.SessionSeatRepository;
import com.cloudticket.activity.persistence.VenueRepository;
import com.cloudticket.activity.service.ActivityService;
import com.cloudticket.common.web.PageResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActivityServiceTest {

  private final ActivityRepository activities = mock(ActivityRepository.class);
  private final SessionRepository sessions = mock(SessionRepository.class);
  private final SessionSeatRepository sessionSeats = mock(SessionSeatRepository.class);
  private final AuditRepository audits = mock(AuditRepository.class);
  private final VenueRepository venues = mock(VenueRepository.class);
  private final ActivityImageRepository images = mock(ActivityImageRepository.class);
  private final ActivityService service = new ActivityService(activities, sessions, sessionSeats, audits, venues);
  private final ActivityService serviceWithImages = new ActivityService(activities, sessions, sessionSeats, audits, venues, images);

  @Test
  void activityWithSessionsCannotBeDeleted() {
    when(sessions.countForActivity("activity-1")).thenReturn(2L);

    assertThrows(IllegalStateException.class, () -> service.delete("activity-1"));
    verify(activities, never()).delete("activity-1");
  }

  @Test
  void activityWithoutSessionsIsDeleted() {
    when(sessions.countForActivity("activity-1")).thenReturn(0L);

    service.delete("activity-1");

    verify(venues).detachFromActivity("activity-1");
    verify(activities).delete("activity-1");
  }

  @Test
  void activityDeletionQueuesItsImagesBeforeRemovingTheActivity() {
    when(sessions.countForActivity("activity-2")).thenReturn(0L);

    serviceWithImages.delete("activity-2");

    verify(images).markDeletePendingForActivity("activity-2");
    verify(activities).delete("activity-2");
  }

  @Test
  void systemConfigReadsTheUnrestrictedListing() {
    when(activities.adminActivities("", "", 0, 20))
        .thenReturn(new PageResult<>(List.of(), 0, 20, 0));

    service.adminActivities("", "", 0, 20, "system:config", "ACTIVITY:activity-1");

    verify(activities).adminActivities("", "", 0, 20);
  }

  @Test
  void activityWildcardAlsoReadsTheUnrestrictedListing() {
    when(activities.adminActivities("", "", 0, 20))
        .thenReturn(new PageResult<>(List.of(), 0, 20, 0));

    service.adminActivities("", "", 0, 20, "activity:read", "ACTIVITY:*");

    verify(activities).adminActivities("", "", 0, 20);
  }

  @Test
  void narrowScopeUsesTheScopedListing() {
    when(activities.adminActivitiesVisibleTo("", "", 0, 20, "ACTIVITY:activity-1"))
        .thenReturn(new PageResult<>(List.of(), 0, 20, 0));

    service.adminActivities("", "", 0, 20, "activity:read", "ACTIVITY:activity-1");

    verify(activities).adminActivitiesVisibleTo("", "", 0, 20, "ACTIVITY:activity-1");
  }

  @Test
  void auditListingIsDelegated() {
    when(audits.page("ACTIVITY_CREATED", 0, 20)).thenReturn(new PageResult<>(List.of(), 0, 20, 0));

    assertEquals(0L, service.audits("ACTIVITY_CREATED", 0, 20).total());
  }
}

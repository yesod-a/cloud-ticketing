package com.cloudticket.activity.service;

import com.cloudticket.activity.domain.Activity;
import com.cloudticket.activity.domain.Audit;
import com.cloudticket.activity.domain.Seat;
import com.cloudticket.activity.persistence.ActivityRepository;
import com.cloudticket.activity.persistence.ActivityImageRepository;
import com.cloudticket.activity.persistence.AuditRepository;
import com.cloudticket.activity.persistence.SessionRepository;
import com.cloudticket.activity.persistence.SessionSeatRepository;
import com.cloudticket.activity.persistence.VenueRepository;
import com.cloudticket.common.security.ResourceScopeRule;
import com.cloudticket.common.web.PageResult;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Activity lifecycle and the read models derived from it. */
@Service
public class ActivityService {

  private final ActivityRepository activities;
  private final SessionRepository sessions;
  private final SessionSeatRepository sessionSeats;
  private final AuditRepository audits;
  private final VenueRepository venues;
  private final ActivityImageRepository images;

  public ActivityService(ActivityRepository activities, SessionRepository sessions,
                          SessionSeatRepository sessionSeats, AuditRepository audits,
                          VenueRepository venues) {
    this(activities, sessions, sessionSeats, audits, venues, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public ActivityService(ActivityRepository activities, SessionRepository sessions,
                          SessionSeatRepository sessionSeats, AuditRepository audits,
                          VenueRepository venues, ActivityImageRepository images) {
    this.activities = activities;
    this.sessions = sessions;
    this.sessionSeats = sessionSeats;
    this.audits = audits;
    this.venues = venues;
    this.images = images;
  }

  public Activity create(String title, String organizer) {
    return activities.create(title, organizer);
  }

  public Activity create(String title, String organizer, String description) {
    return activities.create(title, organizer, description);
  }

  public Activity update(String id, String title, String organizer) {
    return activities.update(id, title, organizer);
  }

  public Activity update(String id, String title, String organizer, String description) {
    return activities.update(id, title, organizer, description);
  }

  public Activity publish(String id) {
    return activities.publish(id);
  }

  public Activity offline(String id) {
    return activities.offline(id);
  }

  public Activity freezeLayout(String id) {
    return activities.freezeLayout(id);
  }

  public Activity require(String id) {
    return activities.require(id);
  }

  public Optional<Activity> find(String id) {
    return activities.find(id);
  }

  @Transactional
  public void delete(String id) {
    if (sessions.countForActivity(id) > 0) {
      throw new IllegalStateException("activity has sessions and cannot be deleted");
    }
    venues.detachFromActivity(id);
    if (images != null) images.markDeletePendingForActivity(id);
    activities.delete(id);
  }

  public List<Activity> publicActivities() {
    return activities.publicActivities();
  }

  public PageResult<Activity> publicActivities(String keyword, String organizer, int page, int size) {
    return activities.publicActivities(keyword, organizer, page, size);
  }

  public PageResult<Activity> adminActivities(String keyword, String status, int page, int size,
                                              String permissions, String scopes) {
    if (ResourceScopeRule.contains(permissions, "system:config")
        || ResourceScopeRule.matches(scopes, "ACTIVITY", "*")) {
      return activities.adminActivities(keyword, status, page, size);
    }
    return activities.adminActivitiesVisibleTo(keyword, status, page, size, scopes);
  }

  public PageResult<Seat> seatsForActivity(String activityId, String permissions, String scopes,
                                           int page, int size) {
    return sessionSeats.pageForActivity(activityId, permissions, scopes, page, size);
  }

  public PageResult<Audit> audits(String action, int page, int size) {
    return audits.page(action, page, size);
  }

  /** Resolves the activity that owns a session seat; used by the scope annotation on seat updates. */
  public String activityIdForSeat(String seatId) {
    return sessionSeats.activityIdForSeat(seatId);
  }

  public String seatSnapshot(String seatId) {
    return sessionSeats.find(seatId).map(Object::toString).orElse(null);
  }
}

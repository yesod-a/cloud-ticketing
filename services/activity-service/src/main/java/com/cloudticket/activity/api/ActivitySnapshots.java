package com.cloudticket.activity.api;

import com.cloudticket.activity.service.ActivityService;
import com.cloudticket.activity.service.VenueService;
import org.springframework.stereotype.Component;

/**
 * Renders the "before" state for {@code @AuditAction} expressions.
 *
 * <p>The audit aspect needs the pre-change value of a resource, which is not a request parameter.
 * These lookups are non-throwing: a missing resource is reported by the use case itself, not by the
 * snapshot, so the error the client sees stays the same.
 */
@Component("activitySnapshots")
public class ActivitySnapshots {

  private final ActivityService activities;
  private final VenueService venues;

  public ActivitySnapshots(ActivityService activities, VenueService venues) {
    this.activities = activities;
    this.venues = venues;
  }

  public String activity(String id) {
    return activities.find(id).map(Object::toString).orElse(null);
  }

  public String venue(String id) {
    return venues.find(id).map(Object::toString).orElse(null);
  }

  public String seat(String id) {
    return activities.seatSnapshot(id);
  }

  public String activityForSeat(String seatId) {
    return activities.activityIdForSeat(seatId);
  }
}

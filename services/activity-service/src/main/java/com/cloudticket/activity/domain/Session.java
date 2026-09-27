package com.cloudticket.activity.domain;

import java.time.Instant;

/** Read model of a session; timestamps are ISO-8601 strings because that is the wire format. */
public record Session(String id, String activityId, String startsAt, String endsAt, String venue,
                      String status, int priceMinor) {

  public Session(String id, String activityId, String startsAt, String endsAt, String venue, String status) {
    this(id, activityId, startsAt, endsAt, venue, status, 0);
  }

  public static Session from(String id, String activityId, Instant startsAt, Instant endsAt, String venue,
                             String status, int priceMinor) {
    return new Session(id, activityId, startsAt.toString(), endsAt.toString(), venue, status, priceMinor);
  }
}

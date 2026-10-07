package com.cloudticket.activity.domain;

import java.time.Instant;

/** Read model of a session; timestamps are ISO-8601 strings because that is the wire format. */
public record Session(String id, String activityId, String startsAt, String endsAt, String venue,
                      String status, int priceMinor, String layoutMode, int capacity, int purchaseLimit,
                      int remainingCapacity, String saleMode, String saleStartAt) {

  public Session(String id, String activityId, String startsAt, String endsAt, String venue, String status) {
    this(id, activityId, startsAt, endsAt, venue, status, 0, "GRID", 0, 0, 0, "DIRECT", null);
  }

  public Session(String id, String activityId, String startsAt, String endsAt, String venue, String status,
                 int priceMinor) {
    this(id, activityId, startsAt, endsAt, venue, status, priceMinor, "GRID", 0, 0, 0, "DIRECT", null);
  }

  public static Session from(String id, String activityId, Instant startsAt, Instant endsAt, String venue,
                             String status, int priceMinor, String layoutMode, int capacity, int purchaseLimit,
                             int remainingCapacity) {
    return new Session(id, activityId, startsAt.toString(), endsAt.toString(), venue, status, priceMinor,
        layoutMode, capacity, purchaseLimit, remainingCapacity, "DIRECT", null);
  }

  public Session(String id, String activityId, String startsAt, String endsAt, String venue,
                 String status, int priceMinor, String layoutMode, int capacity, int purchaseLimit,
                 int remainingCapacity) {
    this(id, activityId, startsAt, endsAt, venue, status, priceMinor, layoutMode, capacity,
        purchaseLimit, remainingCapacity, "DIRECT", null);
  }

  public static Session from(String id, String activityId, Instant startsAt, Instant endsAt, String venue,
                             String status, int priceMinor, String layoutMode, int capacity, int purchaseLimit) {
    return from(id, activityId, startsAt, endsAt, venue, status, priceMinor, layoutMode, capacity,
        purchaseLimit, capacity);
  }

  public static Session from(String id, String activityId, Instant startsAt, Instant endsAt, String venue,
                             String status, int priceMinor, String layoutMode, int capacity, int purchaseLimit,
                             String saleMode) {
    return new Session(id, activityId, startsAt.toString(), endsAt.toString(), venue, status, priceMinor,
        layoutMode, capacity, purchaseLimit, capacity, saleMode, null);
  }

  public static Session from(String id, String activityId, Instant startsAt, Instant endsAt, String venue,
                             String status, int priceMinor, String layoutMode, int capacity, int purchaseLimit,
                             String saleMode, String saleStartAt) {
    return new Session(id, activityId, startsAt.toString(), endsAt.toString(), venue, status, priceMinor,
        layoutMode, capacity, purchaseLimit, capacity, saleMode, saleStartAt);
  }

  public static Session from(String id, String activityId, Instant startsAt, Instant endsAt, String venue,
                             String status, int priceMinor) {
    return from(id, activityId, startsAt, endsAt, venue, status, priceMinor, "GRID", 0, 0, 0);
  }
}

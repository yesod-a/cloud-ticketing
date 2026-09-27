package com.cloudticket.activity.domain;

/** Read model of a venue. */
public record Venue(String id, String activityId, String name, String address, int capacity) {

  public Venue(String id, String activityId, String name, String address) {
    this(id, activityId, name, address, 0);
  }
}

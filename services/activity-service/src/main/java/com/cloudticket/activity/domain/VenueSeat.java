package com.cloudticket.activity.domain;

/** Read model of a venue seat template entry. */
public record VenueSeat(String id, String venueId, String areaLabel, String rowLabel, int seatNumber,
                        String displayName, int x, int y, String seatType, boolean enabled, String status) {}

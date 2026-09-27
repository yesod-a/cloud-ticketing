package com.cloudticket.activity.domain;

/** Read model used when pushing a session's seats into the inventory service. */
public record SeatSnapshot(String id, String areaLabel, String rowLabel, int seatNumber, String displayName,
                           int x, int y, String seatType, String status) {}

package com.cloudticket.activity.domain;

/** Read model behind the public seat picker. */
public record SeatLayout(String id, String areaLabel, String row, int number, String displayName, int x,
                         int y, String type, String status) {}

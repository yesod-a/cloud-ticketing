package com.cloudticket.activity.domain;

/** Read model of a session seat. */
public record Seat(String id, String row, int number, String status) {}

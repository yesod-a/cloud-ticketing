package com.cloudticket.activity.domain;

/** Read model of an activity as the API returns it. */
public record Activity(String id, String title, String organizer, String status, boolean layoutFrozen) {}

package com.cloudticket.activity.domain;

/** Read model of an activity audit entry. */
public record Audit(String id, String actorUserId, String action, String resourceType, String resourceId,
                    String traceId, String createdAt) {}

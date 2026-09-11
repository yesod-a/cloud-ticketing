package com.cloudticket.common.events;

import java.time.Instant;

public record EventEnvelope<T>(String eventId, String eventType, String aggregateType,
                               String aggregateId, Instant occurredAt, int schemaVersion,
                               String traceId, T payload) {
    public EventEnvelope {
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
    }
}

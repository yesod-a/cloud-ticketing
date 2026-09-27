package com.cloudticket.order.event;

import com.cloudticket.order.persistence.entity.OrderOutboxEntity;
import com.cloudticket.order.persistence.mapper.OrderOutboxMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Single place where an order event is turned into an outbox row.
 *
 * <p>Before this class the INSERT was written three times and {@code jsonEscape} was copied four
 * times, each copy hand-concatenating JSON from strings. One wrongly escaped value produced a
 * corrupt message, and one event (PaymentSucceeded) even used a different value type for the same
 * field. The fixed sequence here is: serialise the payload, stamp the envelope metadata, insert.
 * Only the payload varies per event.
 */
@Component
public class OutboxEventWriter {

  private static final String AGGREGATE_TYPE = "ORDER";
  private static final int SCHEMA_VERSION = 1;

  private final OrderOutboxMapper outbox;
  private final ObjectMapper json;

  public OutboxEventWriter(OrderOutboxMapper outbox, ObjectMapper json) {
    this.outbox = outbox;
    this.json = json;
  }

  public void write(String eventType, String aggregateId, Object payload) {
    OrderOutboxEntity event = new OrderOutboxEntity();
    event.setEventId(UUID.randomUUID().toString());
    event.setEventType(eventType);
    event.setAggregateType(AGGREGATE_TYPE);
    event.setAggregateId(aggregateId);
    event.setPayload(serialize(payload));
    event.setTraceId(currentTraceId());
    event.setSchemaVersion(SCHEMA_VERSION);
    outbox.insert(event);
  }

  private String serialize(Object payload) {
    try {
      return json.writeValueAsString(payload);
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException("unable to serialize " + payload.getClass().getSimpleName(), failure);
    }
  }

  private static String currentTraceId() {
    String traceId = MDC.get("traceId");
    return traceId == null || traceId.isBlank() ? null : traceId;
  }
}

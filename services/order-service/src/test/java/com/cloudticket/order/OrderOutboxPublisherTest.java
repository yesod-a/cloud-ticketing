package com.cloudticket.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.order.persistence.entity.OrderOutboxEntity;
import com.cloudticket.order.persistence.mapper.OrderOutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class OrderOutboxPublisherTest {

  @Test
  void publishesPendingEventAndMarksItPublished() {
    OrderOutboxMapper outbox = mock(OrderOutboxMapper.class);
    @SuppressWarnings("unchecked") KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    when(outbox.selectClaimed(anyString(), eq(50))).thenReturn(List.of(event("e-1", "o-1", "{\"orderId\":\"o-1\"}")));
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));

    OutboxPublisher publisher = new OutboxPublisher(outbox, kafka, new ObjectMapper(), "orders", 50, 1000);

    assertEquals(1, publisher.publishOnce());
    verify(outbox).claimPending(anyString(), org.mockito.ArgumentMatchers.argThat(
        until -> until.isAfter(java.time.Instant.now().plusSeconds(54))), eq(50));
    verify(outbox).selectClaimed(anyString(), eq(50));
    verify(kafka).send(eq("orders"), eq("o-1"), contains("\"eventId\":\"e-1\""));
    verify(kafka).send(eq("orders"), eq("o-1"), contains("\"payload\":{\"orderId\":\"o-1\"}"));
    verify(outbox).markPublished(eq("e-1"), anyString());
  }

  @Test
  void recordsFailureWithoutThrowingWhenKafkaSendFails() {
    OrderOutboxMapper outbox = mock(OrderOutboxMapper.class);
    @SuppressWarnings("unchecked") KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    when(outbox.selectClaimed(anyString(), eq(50))).thenReturn(List.of(event("e-2", "o-2", "{}")));
    when(kafka.send(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("broker down"));

    OutboxPublisher publisher = new OutboxPublisher(outbox, kafka, new ObjectMapper(), "orders", 50, 1000);

    assertEquals(0, publisher.publishOnce());
    verify(outbox).recordFailure(eq("e-2"), eq("broker down"), anyString());
  }

  @Test
  void envelopeKeepsTheTraceIdAndSchemaVersionOfTheStoredRow() {
    OrderOutboxMapper outbox = mock(OrderOutboxMapper.class);
    @SuppressWarnings("unchecked") KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    OrderOutboxEntity event = event("e-3", "o-3", "{}");
    event.setTraceId("trace-3");
    when(outbox.selectClaimed(anyString(), eq(50))).thenReturn(List.of(event));
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));

    new OutboxPublisher(outbox, kafka, new ObjectMapper(), "orders", 50, 1000).publishOnce();

    verify(kafka).send(eq("orders"), eq("o-3"),
        org.mockito.ArgumentMatchers.argThat(envelope -> envelope.contains("\"traceId\":\"trace-3\"")
            && envelope.contains("\"schemaVersion\":1")));
  }

  private static OrderOutboxEntity event(String eventId, String aggregateId, String payload) {
    OrderOutboxEntity event = new OrderOutboxEntity();
    event.setEventId(eventId);
    event.setEventType("OrderCreated");
    event.setAggregateType("ORDER");
    event.setAggregateId(aggregateId);
    event.setPayload(payload);
    event.setSchemaVersion(1);
    event.setOccurredAt(Instant.parse("2026-09-16T00:00:00Z"));
    return event;
  }
}

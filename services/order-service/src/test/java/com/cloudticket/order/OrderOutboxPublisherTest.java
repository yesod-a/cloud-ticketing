package com.cloudticket.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class OrderOutboxPublisherTest {
  @Test
  void publishesPendingEventAndMarksItPublished() throws Exception {
    var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
    @SuppressWarnings("unchecked") KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(50)))
        .thenReturn(List.of(Map.of("eventId", "e-1", "aggregateId", "o-1", "payload", "{}")));
    when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

    var publisher = new OutboxPublisher(jdbc, kafka, "orders", 50, 1000);
    assertEquals(1, publisher.publishOnce());

    verify(kafka).send(eq("orders"), eq("o-1"), contains("\"eventId\":\"e-1\""));
    verify(jdbc).update(contains("SET published_at"), eq("e-1"));
  }

  @Test
  void recordsFailureWithoutThrowingWhenKafkaSendFails() {
    var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
    @SuppressWarnings("unchecked") KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(50))).thenReturn(
        List.of(Map.of("eventId", "e-2", "aggregateId", "o-2", "payload", "{}")));
    when(kafka.send(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("broker down"));

    var publisher = new OutboxPublisher(jdbc, kafka, "orders", 50, 1000);
    assertEquals(0, publisher.publishOnce());

    verify(jdbc).update(contains("attempts = attempts + 1"), contains("broker down"), eq("e-2"));
  }
}

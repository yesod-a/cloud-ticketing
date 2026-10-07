package com.cloudticket.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class OrderTimeoutZsetServiceTest {

  @Test
  void schedulesAndAcknowledgesOrderTimeouts() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    var zsets = mock(org.springframework.data.redis.core.ZSetOperations.class);
    when(redis.opsForZSet()).thenReturn(zsets);
    OrderTimeoutZsetService service = new OrderTimeoutZsetService(redis);

    Instant due = Instant.parse("2026-10-07T10:00:00Z");
    service.schedule("order-1", due);
    service.acknowledge("order-1");

    verify(zsets).add(OrderTimeoutZsetService.PENDING_KEY, "order-1", due.toEpochMilli());
    verify(zsets).remove(OrderTimeoutZsetService.PROCESSING_KEY, "order-1");
  }

  @Test
  void claimsDueOrdersThroughAtomicRedisScript() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    when(redis.execute(any(RedisScript.class), eq(List.of(
        OrderTimeoutZsetService.PENDING_KEY,
        OrderTimeoutZsetService.PROCESSING_KEY)), any(Object[].class)))
        .thenReturn(List.of("order-1"));
    OrderTimeoutZsetService service = new OrderTimeoutZsetService(redis);

    assertEquals(List.of("order-1"), service.claimDue(10, 1234L, 5000L));
  }
}

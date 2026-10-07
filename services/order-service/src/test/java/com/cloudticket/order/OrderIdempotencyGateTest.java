package com.cloudticket.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cloudticket.order.client.OrderIdempotencyGate;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class OrderIdempotencyGateTest {
  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  private final ValueOperations<String, String> values = mock(ValueOperations.class);

  @Test
  void sameRequestCanBeIdentifiedBeforeOrderMysqlInsert() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

    var claim = new OrderIdempotencyGate(redis, true, 900)
        .claim("idem-1", "hash-1", "order-1");

    assertEquals(true, claim.owner());
    assertEquals("order-1", claim.orderId());
  }

  @Test
  void sameKeyWithDifferentRequestIsRejectedAtRedisGate() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
    when(values.get(anyString())).thenReturn("hash-1|order-1");

    assertThrows(IdempotencyConflictException.class, () ->
        new OrderIdempotencyGate(redis, true, 900).claim("idem-1", "hash-2", "order-2"));
  }
}

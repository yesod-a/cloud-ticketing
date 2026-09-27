package com.cloudticket.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.inventory.redis.RedisSeatLockService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisSeatLockServiceTest {

  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

  @Test
  void reservesAllSeatsWithOneAtomicScriptAndSessionHashTag() {
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
    var service = new RedisSeatLockService(redis, true);

    assertTrue(service.reserveIndexed("order-1", "session-1", List.of(4, 8), 60));

    verify(redis).execute(any(RedisScript.class),
        org.mockito.ArgumentMatchers.eq(List.of(
            "cloudticket:inventory:{session-1}:sold",
            "cloudticket:inventory:{session-1}:locked",
            "cloudticket:inventory:{session-1}:disabled",
            "cloudticket:inventory:{session-1}:hold:4",
            "cloudticket:inventory:{session-1}:hold:8")),
        org.mockito.ArgumentMatchers.eq(new Object[] {"order-1:session-1", "60000", "4", "8"}));
  }

  @Test
  void conflictReturnsFalseWithoutPartialSuccess() {
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);
    var service = new RedisSeatLockService(redis, true);

    assertFalse(service.reserveIndexed("order-1", "session-1", List.of(1, 2), 60));
  }

  @Test
  void releaseUsesTokenCheckingScript() {
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
    var service = new RedisSeatLockService(redis, true);

    assertTrue(service.releaseIndexed("order-1", "session-1", List.of(1)));
    verify(redis).execute(any(RedisScript.class),
        org.mockito.ArgumentMatchers.eq(List.of("cloudticket:inventory:{session-1}:hold:1")),
        org.mockito.ArgumentMatchers.eq("order-1:session-1"));
  }

  @Test
  void disabledModeDoesNotContactRedis() {
    var service = new RedisSeatLockService(redis, false);

    assertTrue(service.reserve("order-1", "session-1", List.of("seat-1"), 60));
    assertTrue(service.release("order-1", "session-1", List.of("seat-1")));
    verify(redis, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
  }

  @Test
  void soldBitmapIsPassedToAtomicScript() {
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);
    assertFalse(new RedisSeatLockService(redis, true)
        .reserveIndexed("order-1", "session-1", List.of(3), 60));
    verify(redis).execute(any(RedisScript.class),
        org.mockito.ArgumentMatchers.argThat(keys -> keys.get(0).endsWith(":sold")),
        any(Object[].class));
  }
}

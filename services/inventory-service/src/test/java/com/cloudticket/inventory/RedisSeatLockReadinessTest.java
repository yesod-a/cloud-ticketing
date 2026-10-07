package com.cloudticket.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cloudticket.inventory.cache.InventoryReadService;
import com.cloudticket.inventory.redis.RedisSeatLockService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisSeatLockReadinessTest {
  @Test
  void redisLockDoesNotUseMissingProjectionAsAllAvailable() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    InventoryReadService reads = mock(InventoryReadService.class);
    when(reads.ensureReady("session-1")).thenReturn(false);

    var service = new RedisSeatLockService(redis, true, reads);

    assertFalse(service.reserveIndexed("order-1", "session-1", List.of(1), 60));
  }
}

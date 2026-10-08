package com.cloudticket.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/** Redis is only the timeout scheduler; MySQL remains the order state source of truth. */
@Component
public class OrderTimeoutZsetService {
  public static final String PENDING_KEY = "cloudticket:order:timeout:pending";
  public static final String PROCESSING_KEY = "cloudticket:order:timeout:processing";
  private static final RedisScript<List> CLAIM_SCRIPT = script("redis/claim-order-timeouts.lua");
  private static final RedisScript<Long> REQUEUE_SCRIPT = script("redis/requeue-order-timeouts.lua");

  private final StringRedisTemplate redis;

  public OrderTimeoutZsetService(StringRedisTemplate redis) { this.redis = redis; }

  public void schedule(String orderId, Instant expireAt) {
    redis.opsForZSet().add(PENDING_KEY, orderId, expireAt.toEpochMilli());
  }

  public void acknowledge(String orderId) {
    redis.opsForZSet().remove(PROCESSING_KEY, orderId);
  }

  /** Removes a task regardless of whether it is still pending or currently leased. */
  public void remove(String orderId) {
    redis.opsForZSet().remove(PENDING_KEY, orderId);
    redis.opsForZSet().remove(PROCESSING_KEY, orderId);
  }

  public List<String> claimDue(int limit, long nowMillis, long leaseUntilMillis) {
    List<?> result = redis.execute(CLAIM_SCRIPT, List.of(PENDING_KEY, PROCESSING_KEY),
        Long.toString(nowMillis), Integer.toString(Math.max(1, limit)), Long.toString(leaseUntilMillis));
    if (result == null || result.isEmpty()) return List.of();
    List<String> ids = new ArrayList<>(result.size());
    result.forEach(value -> ids.add(String.valueOf(value)));
    return ids;
  }

  public long requeueExpiredClaims(long nowMillis, int limit) {
    Long result = redis.execute(REQUEUE_SCRIPT, List.of(PROCESSING_KEY, PENDING_KEY),
        Long.toString(nowMillis), Integer.toString(Math.max(1, limit)));
    return result == null ? 0L : result;
  }

  private static <T> RedisScript<T> script(String path) {
    DefaultRedisScript<T> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource(path));
    return script;
  }
}

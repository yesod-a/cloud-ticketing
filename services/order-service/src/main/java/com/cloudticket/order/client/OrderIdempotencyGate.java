package com.cloudticket.order.client;

import com.cloudticket.order.IdempotencyConflictException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Fast duplicate-request gate; the unique database key remains the final idempotency authority. */
@Component
public class OrderIdempotencyGate {
  private static final String PREFIX = "cloudticket:order:idempotency:";
  private final StringRedisTemplate redis;
  private final boolean enabled;
  private final Duration ttl;

  public OrderIdempotencyGate(
      StringRedisTemplate redis,
      @Value("${cloudticket.order-idempotency.enabled:true}") boolean enabled,
      @Value("${cloudticket.order-idempotency.ttl-seconds:900}") long ttlSeconds) {
    this.redis = redis;
    this.enabled = enabled && redis != null;
    this.ttl = Duration.ofSeconds(Math.max(60, ttlSeconds));
  }

  public Claim claim(String idempotencyKey, String requestHash, String orderId) {
    if (!enabled) return Claim.unavailable();
    String key = PREFIX + idempotencyKey.trim();
    String value = requestHash + "|" + orderId;
    try {
      if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, value, ttl))) {
        return new Claim(true, true, orderId);
      }
      String existing = redis.opsForValue().get(key);
      if (existing == null || existing.isBlank()) return Claim.unavailable();
      int separator = existing.lastIndexOf('|');
      if (separator <= 0 || !requestHash.equals(existing.substring(0, separator))) {
        throw new IdempotencyConflictException();
      }
      return new Claim(true, false, existing.substring(separator + 1));
    } catch (IdempotencyConflictException conflict) {
      throw conflict;
    } catch (RuntimeException unavailable) {
      return Claim.unavailable();
    }
  }

  public void release(Claim claim, String idempotencyKey) {
    if (!enabled || claim == null || !claim.owner()) return;
    try {
      redis.delete(PREFIX + idempotencyKey.trim());
    } catch (RuntimeException ignored) {
      // TTL removes an abandoned claim if Redis is unavailable during compensation.
    }
  }

  public record Claim(boolean available, boolean owner, String orderId) {
    static Claim unavailable() { return new Claim(false, false, null); }
  }
}

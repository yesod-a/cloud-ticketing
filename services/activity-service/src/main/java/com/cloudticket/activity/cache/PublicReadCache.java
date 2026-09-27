package com.cloudticket.activity.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Two-level cache for public activity reads.
 *
 * <p>Cache keys carry a generation number that is bumped by every administrative write, so a
 * mutation makes all previously stored entries unreachable at once instead of forcing pattern
 * deletes. L1 is per-instance, L2 is shared through Redis, and both expire by TTL.
 */
public class PublicReadCache {
  private static final Logger log = LoggerFactory.getLogger(PublicReadCache.class);
  private static final String KEY_PREFIX = "cloudticket:activity:public-read:";
  private static final String GENERATION_KEY = "cloudticket:activity:public-read:gen";
  private static final long GENERATION_REFRESH_MS = 1000;

  private final Cache<String, String> local;
  private final StringRedisTemplate redis;
  private final boolean enabled;
  private final Duration redisTtl;
  private final AtomicLong localGeneration = new AtomicLong(1);
  private volatile long generationReadAt;

  public PublicReadCache(boolean enabled, long localTtlMs, long redisTtlMs, StringRedisTemplate redis) {
    this.enabled = enabled;
    this.redis = redis;
    this.redisTtl = Duration.ofMillis(Math.max(1, redisTtlMs));
    this.local = Caffeine.newBuilder()
        .maximumSize(5_000)
        .expireAfterWrite(Duration.ofMillis(Math.max(1, localTtlMs)))
        .build();
  }

  public boolean enabled() {
    return enabled;
  }

  public String key(String method, String path, String query) {
    String suffix = query == null || query.isBlank() ? "" : "?" + query;
    return generation() + "|" + method + " " + path + suffix;
  }

  public Optional<String> get(String key) {
    String hit = local.getIfPresent(key);
    if (hit != null) return Optional.of(hit);
    if (redis == null) return Optional.empty();
    try {
      String value = redis.opsForValue().get(KEY_PREFIX + key);
      if (value == null) return Optional.empty();
      local.put(key, value);
      return Optional.of(value);
    } catch (RuntimeException failure) {
      log.warn("Redis read failed, serving from the database: {}", failure.toString());
      return Optional.empty();
    }
  }

  public void put(String key, String body) {
    local.put(key, body);
    if (redis == null) return;
    try {
      redis.opsForValue().set(KEY_PREFIX + key, body, redisTtl);
    } catch (RuntimeException failure) {
      log.warn("Redis write failed, entry kept in the local cache only: {}", failure.toString());
    }
  }

  /** Invalidates all cached public responses by moving every instance to the next generation. */
  public void invalidate() {
    local.invalidateAll();
    if (redis != null) {
      try {
        Long next = redis.opsForValue().increment(GENERATION_KEY);
        if (next != null) {
          localGeneration.set(next);
          generationReadAt = System.currentTimeMillis();
          return;
        }
      } catch (RuntimeException failure) {
        log.warn("Redis generation bump failed, invalidating the local cache only: {}", failure.toString());
      }
    }
    localGeneration.incrementAndGet();
    generationReadAt = System.currentTimeMillis();
  }

  private long generation() {
    long now = System.currentTimeMillis();
    if (redis == null || now - generationReadAt < GENERATION_REFRESH_MS) return localGeneration.get();
    generationReadAt = now;
    try {
      String value = redis.opsForValue().get(GENERATION_KEY);
      if (value != null) localGeneration.set(Long.parseLong(value));
      else redis.opsForValue().setIfAbsent(GENERATION_KEY, String.valueOf(localGeneration.get()));
    } catch (RuntimeException failure) {
      log.debug("Unable to refresh the cache generation: {}", failure.toString());
    }
    return localGeneration.get();
  }
}

package com.cloudticket.gateway.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

/**
 * Two-level cache for gateway token-status lookups.
 *
 * <p>L1 is a per-instance Caffeine cache, L2 is a shared Redis entry, and the remote auth-service
 * check is only executed on a miss. Both levels expire by TTL, so a revoked token stops working
 * within the configured staleness window instead of never.
 */
public class TokenStatusCache {
  private static final Logger log = LoggerFactory.getLogger(TokenStatusCache.class);
  private static final String KEY_PREFIX = "cloudticket:gateway:token-status:";

  private final Cache<String, Boolean> local;
  private final ReactiveStringRedisTemplate redis;
  private final boolean enabled;
  private final Duration redisTtl;

  public TokenStatusCache(boolean enabled, long localTtlMs, long redisTtlMs, ReactiveStringRedisTemplate redis) {
    this.enabled = enabled;
    this.redis = redis;
    this.redisTtl = Duration.ofMillis(Math.max(1, redisTtlMs));
    this.local = Caffeine.newBuilder()
        .maximumSize(50_000)
        .expireAfterWrite(Duration.ofMillis(Math.max(1, localTtlMs)))
        .build();
  }

  public boolean enabled() {
    return enabled;
  }

  /** Resolves the status from L1, then L2, and finally the remote loader. */
  public Mono<Boolean> resolve(String key, Mono<Boolean> loader) {
    if (!enabled) return loader;
    Boolean hit = local.getIfPresent(key);
    if (hit != null) return Mono.just(hit);
    if (redis == null) return load(key, loader);
    Mono<Boolean> fromRedis = redis.opsForValue().get(KEY_PREFIX + key)
        .map("1"::equals)
        .doOnNext(value -> local.put(key, value))
        .onErrorResume(error -> {
          log.warn("Token status cache lookup failed, falling back to the auth service: {}", error.toString());
          return Mono.empty();
        });
    return fromRedis.switchIfEmpty(Mono.defer(() -> load(key, loader)));
  }

  private Mono<Boolean> load(String key, Mono<Boolean> loader) {
    return loader.doOnNext(active -> {
      local.put(key, active);
      if (redis != null) {
        redis.opsForValue().set(KEY_PREFIX + key, active ? "1" : "0", redisTtl)
            .subscribe(ignored -> { }, error -> log.debug("Unable to cache token status in Redis: {}", error.toString()));
      }
    });
  }
}

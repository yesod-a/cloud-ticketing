package com.cloudticket.gateway.config;

import com.cloudticket.gateway.security.TokenStatusCache;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/** Wires the gateway token-status cache. Set {@code cloudticket.auth-status-cache.enabled=false} to bypass it. */
@Configuration
public class CacheConfiguration {
  @Bean
  TokenStatusCache tokenStatusCache(
      @Value("${cloudticket.auth-status-cache.enabled:true}") boolean enabled,
      @Value("${cloudticket.auth-status-cache.local-ttl-ms:1000}") long localTtlMs,
      @Value("${cloudticket.auth-status-cache.redis-ttl-ms:3000}") long redisTtlMs,
      ObjectProvider<ReactiveStringRedisTemplate> redis) {
    return new TokenStatusCache(enabled, localTtlMs, redisTtlMs, enabled ? redis.getIfAvailable() : null);
  }
}

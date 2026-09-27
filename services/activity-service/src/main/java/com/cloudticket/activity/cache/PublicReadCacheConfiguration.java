package com.cloudticket.activity.cache;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Wires the public read cache. Set {@code cloudticket.public-read-cache.enabled=false} to bypass it. */
@Configuration
public class PublicReadCacheConfiguration {
  @Bean
  PublicReadCache publicReadCache(
      @Value("${cloudticket.public-read-cache.enabled:true}") boolean enabled,
      @Value("${cloudticket.public-read-cache.local-ttl-ms:3000}") long localTtlMs,
      @Value("${cloudticket.public-read-cache.redis-ttl-ms:30000}") long redisTtlMs,
      ObjectProvider<StringRedisTemplate> redis) {
    return new PublicReadCache(enabled, localTtlMs, redisTtlMs, enabled ? redis.getIfAvailable() : null);
  }

  @Bean
  FilterRegistrationBean<PublicReadCacheFilter> publicReadCacheFilter(PublicReadCache cache) {
    FilterRegistrationBean<PublicReadCacheFilter> registration = new FilterRegistrationBean<>(new PublicReadCacheFilter(cache));
    // Registered on every path so that administrative writes can invalidate the cache; the filter
    // itself decides what to cache and what to invalidate.
    registration.addUrlPatterns("/*");
    registration.setOrder(Ordered.LOWEST_PRECEDENCE);
    return registration;
  }
}

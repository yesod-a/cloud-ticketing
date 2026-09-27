package com.cloudticket.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudticket.gateway.security.TokenStatusCache;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class TokenStatusCacheTest {
  private static Mono<Boolean> counting(AtomicInteger calls, boolean value) {
    return Mono.defer(() -> {
      calls.incrementAndGet();
      return Mono.just(value);
    });
  }

  @Test
  void servesRepeatedLookupsFromTheLocalCache() {
    var calls = new AtomicInteger();
    var cache = new TokenStatusCache(true, 60_000, 60_000, null);

    assertTrue(cache.resolve("u-1:0:j-1", counting(calls, true)).block());
    assertTrue(cache.resolve("u-1:0:j-1", counting(calls, true)).block());

    assertEquals(1, calls.get());
  }

  @Test
  void cachesNegativeResultsSoRevokedTokensDoNotHammerTheAuthService() {
    var calls = new AtomicInteger();
    var cache = new TokenStatusCache(true, 60_000, 60_000, null);

    assertFalse(cache.resolve("u-1:0:j-2", counting(calls, false)).block());
    assertFalse(cache.resolve("u-1:0:j-2", counting(calls, false)).block());

    assertEquals(1, calls.get());
  }

  @Test
  void bypassesTheCacheWhenDisabled() {
    var calls = new AtomicInteger();
    var cache = new TokenStatusCache(false, 60_000, 60_000, null);

    cache.resolve("u-1:0:j-3", counting(calls, true)).block();
    cache.resolve("u-1:0:j-3", counting(calls, true)).block();

    assertFalse(cache.enabled());
    assertEquals(2, calls.get());
  }
}

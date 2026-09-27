package com.cloudticket.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudticket.activity.cache.PublicReadCache;
import org.junit.jupiter.api.Test;

class PublicReadCacheTest {
  @Test
  void storesAndServesEntriesFromTheLocalCache() {
    var cache = new PublicReadCache(true, 60_000, 60_000, null);
    String key = cache.key("GET", "/api/activities", "page=0&size=12");

    cache.put(key, "{\"code\":\"OK\"}");

    assertEquals("{\"code\":\"OK\"}", cache.get(key).orElseThrow());
  }

  @Test
  void invalidationMakesPreviouslyStoredEntriesUnreachable() {
    var cache = new PublicReadCache(true, 60_000, 60_000, null);
    String before = cache.key("GET", "/api/activities", null);
    cache.put(before, "{\"total\":1}");

    cache.invalidate();
    String after = cache.key("GET", "/api/activities", null);

    assertNotEquals(before, after);
    assertTrue(cache.get(before).isEmpty());
    assertTrue(cache.get(after).isEmpty());
  }

  @Test
  void disabledCacheAdvertisesItselfAsOff() {
    var cache = new PublicReadCache(false, 60_000, 60_000, null);

    assertFalse(cache.enabled());
  }
}

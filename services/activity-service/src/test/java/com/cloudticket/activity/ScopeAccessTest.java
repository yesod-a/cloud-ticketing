package com.cloudticket.activity;

import static org.junit.jupiter.api.Assertions.*;

import com.cloudticket.activity.security.ScopeAccess;
import org.junit.jupiter.api.Test;

class ScopeAccessTest {
  @Test void systemConfigCanReadAnyResource() {
    assertTrue(ScopeAccess.allows("system:config", "", "ACTIVITY", "a1"));
  }

  @Test void activityScopeCoversChildSessionAndVenue() {
    String scopes = "ACTIVITY:a1";
    assertTrue(ScopeAccess.allows(scopes, "ACTIVITY", "a1"));
    assertTrue(ScopeAccess.allows("", scopes, "SESSION", "s1", "a1"));
    assertTrue(ScopeAccess.allows("", scopes, "VENUE", "v1", "a1"));
    assertFalse(ScopeAccess.allows(scopes, "ACTIVITY", "a2"));
  }

  @Test void emptyScopesDoNotGrantScopedResource() {
    assertFalse(ScopeAccess.allows("activity:read", "ACTIVITY", "a1"));
  }
}

package com.cloudticket.common.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResourceScopeRuleTest {

  @Test
  void systemConfigCanReadAnyResource() {
    assertTrue(ResourceScopeRule.allows("system:config", "", "ACTIVITY", "a1"));
  }

  @Test
  void activityScopeCoversChildSessionAndVenue() {
    String scopes = "ACTIVITY:a1";

    assertTrue(ResourceScopeRule.allows("", scopes, "ACTIVITY", "a1"));
    assertTrue(ResourceScopeRule.allows("", scopes, "SESSION", "s1", "a1"));
    assertTrue(ResourceScopeRule.allows("", scopes, "VENUE", "v1", "a1"));
    assertFalse(ResourceScopeRule.allows("", scopes, "ACTIVITY", "a2"));
  }

  @Test
  void exactSessionScopeIsHonoured() {
    assertTrue(ResourceScopeRule.allows("order:read", "SESSION:s1", "SESSION", "s1"));
    assertFalse(ResourceScopeRule.allows("order:read", "SESSION:s2", "SESSION", "s1"));
  }

  @Test
  void wildcardScopeCoversEveryResourceOfThatType() {
    assertTrue(ResourceScopeRule.allows("", "SESSION:*", "SESSION", "anything"));
    assertTrue(ResourceScopeRule.allows("", "ACTIVITY:*", "VENUE", "v1", "a1"));
  }

  @Test
  void emptyScopesDoNotGrantScopedAccess() {
    assertFalse(ResourceScopeRule.allows("order:read", "", "SESSION", "s1"));
    assertFalse(ResourceScopeRule.allows("order:read", null, "SESSION", "s1"));
  }

  @Test
  void blankIdentifiersAreNeverAllowed() {
    assertFalse(ResourceScopeRule.allows("order:read", "SESSION:*", "SESSION", "  "));
    assertFalse(ResourceScopeRule.allows("order:read", "SESSION:*", "SESSION", null));
  }

  @Test
  void scopeMatchingIgnoresCaseAndSurroundingSpaces() {
    assertTrue(ResourceScopeRule.allows("", " session:s1 , activity:a1 ", "SESSION", "s1"));
  }

  @Test
  void containsMatchesASinglePermissionFromTheHeader() {
    assertTrue(ResourceScopeRule.contains("activity:read,activity:write", "activity:write"));
    assertFalse(ResourceScopeRule.contains("activity:read", "activity:write"));
    assertFalse(ResourceScopeRule.contains("", "activity:read"));
  }
}

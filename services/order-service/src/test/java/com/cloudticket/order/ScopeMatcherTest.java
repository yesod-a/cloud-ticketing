package com.cloudticket.order;

import static org.junit.jupiter.api.Assertions.*;
import com.cloudticket.order.security.ScopeMatcher;
import org.junit.jupiter.api.Test;

class ScopeMatcherTest {
  @Test void allowsAnExactResourceScope() {
    assertTrue(ScopeMatcher.allows("order:read", "SESSION:s1", "SESSION", "s1"));
    assertFalse(ScopeMatcher.allows("order:read", "SESSION:s2", "SESSION", "s1"));
  }
  @Test void systemConfigBypassesResourceScope() {
    assertTrue(ScopeMatcher.allows("system:config", "", "SESSION", "s1"));
  }
  @Test void emptyScopesDoNotGrantScopedAccess() {
    assertFalse(ScopeMatcher.allows("order:read", "", "SESSION", "s1"));
  }
}

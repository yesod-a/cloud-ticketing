package com.cloudticket.gateway;

import static org.junit.jupiter.api.Assertions.*;
import com.cloudticket.gateway.security.AnonymousPathPolicy;
import org.junit.jupiter.api.Test;

class AnonymousPathPolicyTest {
  private final AnonymousPathPolicy policy = new AnonymousPathPolicy();
  @Test void permitsOnlyAuthenticationAndPublishedReadRoutes() {
    assertTrue(policy.allows("POST", "/api/auth/login"));
    assertTrue(policy.allows("GET", "/api/activities"));
    assertTrue(policy.allows("GET", "/api/activities/abc"));
    assertFalse(policy.allows("POST", "/api/orders"));
    assertFalse(policy.allows("GET", "/api/admin/activities"));
    assertFalse(policy.allows("GET", "/internal/inventory/lock"));
  }
}

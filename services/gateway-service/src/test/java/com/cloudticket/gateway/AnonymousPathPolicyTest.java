package com.cloudticket.gateway;

import static org.junit.jupiter.api.Assertions.*;
import com.cloudticket.gateway.security.AnonymousPathPolicy;
import org.junit.jupiter.api.Test;

class AnonymousPathPolicyTest {
  private final AnonymousPathPolicy policy = new AnonymousPathPolicy();
  @Test void permitsOnlyAuthenticationAndPublishedReadRoutes() {
    assertTrue(policy.allows("POST", "/api/auth/login"));
    assertTrue(policy.allows("POST", "/api/auth/register"));
    assertTrue(policy.allows("POST", "/api/auth/refresh"));
    assertFalse(policy.allows("GET", "/api/auth/me"));
    assertTrue(policy.allows("GET", "/api/auth/avatars/00000000-0000-0000-0000-000000000001"));
    assertFalse(policy.allows("POST", "/api/auth/me/password"));
    assertFalse(policy.allows("POST", "/api/auth/logout"));
    assertTrue(policy.allows("GET", "/api/activities"));
    assertTrue(policy.allows("GET", "/api/activities/abc"));
    assertTrue(policy.allows("GET", "/api/sessions/abc/seats"));
    assertFalse(policy.allows("POST", "/api/orders"));
    assertFalse(policy.allows("GET", "/api/admin/activities"));
    assertFalse(policy.allows("GET", "/internal/inventory/lock"));
  }
}

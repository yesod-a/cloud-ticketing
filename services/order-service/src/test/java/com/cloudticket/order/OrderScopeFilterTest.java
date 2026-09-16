package com.cloudticket.order;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrderScopeFilterTest {
  @Test void scopedAdminSeesOnlyMatchingSession() {
    assertTrue(OrderScopeFilter.visible(Map.of("sessionId", "s1"), "order:read", "SESSION:s1"));
    assertFalse(OrderScopeFilter.visible(Map.of("sessionId", "s2"), "order:read", "SESSION:s1"));
  }
}

package com.cloudticket.inventory;

import static org.junit.jupiter.api.Assertions.*;
import com.cloudticket.inventory.security.InventoryScopeAccess;
import org.junit.jupiter.api.Test;

class InventoryScopeAccessTest {
  @Test void activityOrSessionScopeAllowsSeat() {
    assertTrue(InventoryScopeAccess.allows("inventory:read", "ACTIVITY:a1", "s1", "a1"));
    assertTrue(InventoryScopeAccess.allows("inventory:read", "SESSION:s1", "s1", "a2"));
    assertFalse(InventoryScopeAccess.allows("inventory:read", "SESSION:s2", "s1", "a1"));
  }

  @Test void systemConfigBypassesScope() {
    assertTrue(InventoryScopeAccess.allows("system:config", "", "s1", "a1"));
  }
}

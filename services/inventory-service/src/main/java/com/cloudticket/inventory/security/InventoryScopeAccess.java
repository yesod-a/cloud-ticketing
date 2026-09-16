package com.cloudticket.inventory.security;

import java.util.Arrays;

public final class InventoryScopeAccess {
  private InventoryScopeAccess() {}

  public static boolean allows(String permissions, String scopes, String sessionId, String activityId) {
    if (contains(permissions, "system:config")) return true;
    return matches(scopes, "SESSION", sessionId) || matches(scopes, "SESSION", "*")
        || matches(scopes, "ACTIVITY", activityId) || matches(scopes, "ACTIVITY", "*");
  }

  private static boolean matches(String scopes, String type, String id) {
    if (scopes == null || scopes.isBlank() || id == null || id.isBlank()) return false;
    String wanted = type + ":" + id;
    return Arrays.stream(scopes.split(",")).map(String::trim).anyMatch(v -> v.equalsIgnoreCase(wanted));
  }

  private static boolean contains(String values, String wanted) {
    if (values == null || values.isBlank()) return false;
    return Arrays.stream(values.split(",")).map(String::trim).anyMatch(v -> v.equalsIgnoreCase(wanted));
  }
}

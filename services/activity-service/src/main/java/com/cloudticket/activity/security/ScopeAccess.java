package com.cloudticket.activity.security;

import java.util.Arrays;

/** Centralized resource-scope checks for activity-service admin reads and writes. */
public final class ScopeAccess {
  private ScopeAccess() {}

  public static boolean allows(String permissions, String scopes, String resourceType, String resourceId) {
    return allows(permissions, scopes, resourceType, resourceId, null);
  }

  public static boolean allows(String scopes, String resourceType, String resourceId) {
    return allows("", scopes, resourceType, resourceId, null);
  }

  public static boolean allows(String permissions, String scopes, String resourceType, String resourceId, String parentActivityId) {
    if (contains(permissions, "system:config")) return true;
    if (resourceType == null || resourceId == null || resourceId.isBlank()) return false;
    String type = resourceType.trim().toUpperCase();
    if (matches(scopes, type, resourceId) || matches(scopes, type, "*")) return true;
    return parentActivityId != null && !parentActivityId.isBlank()
        && (matches(scopes, "ACTIVITY", parentActivityId) || matches(scopes, "ACTIVITY", "*"));
  }

  private static boolean matches(String scopes, String type, String id) {
    if (scopes == null || scopes.isBlank()) return false;
    String wanted = type + ":" + id;
    return Arrays.stream(scopes.split(","))
        .map(String::trim)
        .anyMatch(value -> value.equalsIgnoreCase(wanted));
  }

  private static boolean contains(String values, String wanted) {
    if (values == null || values.isBlank()) return false;
    return Arrays.stream(values.split(","))
        .map(String::trim)
        .anyMatch(value -> value.equalsIgnoreCase(wanted));
  }
}

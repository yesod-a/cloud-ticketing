package com.cloudticket.common.security;

import java.util.Arrays;
import java.util.Locale;

/**
 * Single implementation of the {@code TYPE:id} scope rule shared by every service.
 *
 * <p>Before this class existed the same matcher was copied into activity, inventory and order
 * services with slightly different casing rules; one copy is easier to reason about and to test.
 */
public final class ResourceScopeRule {

  private ResourceScopeRule() {}

  public static boolean allows(String permissions, String scopes, String resourceType, String resourceId) {
    return allows(permissions, scopes, resourceType, resourceId, null);
  }

  /**
   * @param parentId owning resource id (usually the activity) that also grants access to children
   */
  public static boolean allows(String permissions, String scopes, String resourceType,
                               String resourceId, String parentId) {
    if (contains(permissions, "system:config")) return true;
    if (isBlank(resourceType) || isBlank(resourceId)) return false;
    String type = resourceType.trim().toUpperCase(Locale.ROOT);
    if (matches(scopes, type, resourceId) || matches(scopes, type, "*")) return true;
    return !isBlank(parentId) && (matches(scopes, "ACTIVITY", parentId) || matches(scopes, "ACTIVITY", "*"));
  }

  public static boolean matches(String scopes, String resourceType, String resourceId) {
    if (isBlank(scopes) || isBlank(resourceType) || isBlank(resourceId)) return false;
    String wanted = resourceType.trim().toUpperCase(Locale.ROOT) + ":" + resourceId.trim();
    return Arrays.stream(scopes.split(","))
        .map(String::trim)
        .anyMatch(value -> value.equalsIgnoreCase(wanted));
  }

  public static boolean contains(String values, String wanted) {
    if (isBlank(values) || isBlank(wanted)) return false;
    return Arrays.stream(values.split(","))
        .map(String::trim)
        .anyMatch(value -> value.equalsIgnoreCase(wanted.trim()));
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}

package com.cloudticket.order.security;

import java.util.Arrays;

public final class ScopeMatcher {
  private ScopeMatcher() {}

  public static boolean allows(String permissions, String scopes, String resourceType, String resourceId) {
    if (permissions != null && permissions.contains("system:config")) return true;
    if (scopes == null || scopes.isBlank() || resourceId == null || resourceId.isBlank()) return false;
    String wanted = resourceType.toUpperCase() + ":" + resourceId;
    return Arrays.stream(scopes.split(",")).map(String::trim).anyMatch(value -> value.equals(wanted) || value.equals(resourceType.toUpperCase() + ":*"));
  }
}

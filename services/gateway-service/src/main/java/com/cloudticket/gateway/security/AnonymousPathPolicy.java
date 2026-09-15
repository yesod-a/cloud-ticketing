package com.cloudticket.gateway.security;

public final class AnonymousPathPolicy {
  public boolean allows(String method, String path) {
    if (path.startsWith("/api/auth/")) return true;
    return "GET".equalsIgnoreCase(method) && ("/api/activities".equals(path) || path.matches("/api/activities/[^/]+") || path.matches("/api/sessions/[^/]+"));
  }
}

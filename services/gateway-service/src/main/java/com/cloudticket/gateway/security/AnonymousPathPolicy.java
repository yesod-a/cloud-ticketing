package com.cloudticket.gateway.security;

public final class AnonymousPathPolicy {
  public boolean allows(String method, String path) {
    if ("POST".equalsIgnoreCase(method) && (
        "/api/auth/login".equals(path)
        || "/api/auth/register".equals(path)
        || "/api/auth/refresh".equals(path)
        || "/api/auth/password/forgot".equals(path)
        || "/api/auth/password/reset".equals(path))) return true;
    if ("GET".equalsIgnoreCase(method) && (path.matches("/api/auth/avatars/[0-9a-fA-F-]{36}") || "/api/auth/public-profiles".equals(path))) return true;
    return "GET".equalsIgnoreCase(method) && ("/api/activities".equals(path) || path.matches("/api/activities/[^/]+") || path.matches("/api/activities/[^/]+/comments") || path.matches("/api/comments/[^/]+/replies") || path.matches("/api/sessions/[^/]+") || path.matches("/api/sessions/[^/]+/seats"));
  }
}

package com.cloudticket.auth.api;

/** Typed request bodies for the auth admin API. */
public final class AuthCommands {

  private AuthCommands() {}

  public record ChangeUserStatus(String status) {
    public String statusOrDefault() {
      return status == null || status.isBlank() ? "ACTIVE" : status;
    }
  }

  public record GrantRole(String roleCode) {}

  public record CreateScope(String resourceType, String resourceId) {}
}

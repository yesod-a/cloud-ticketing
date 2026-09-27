package com.cloudticket.common.security;

/**
 * Identity the gateway forwards to a service through request headers.
 *
 * <p>Holding it in one immutable record keeps the aspect free of servlet types, which also makes
 * authorization testable without spinning up a web container.
 */
public record CallerContext(String permissions, String scopes, String userId, String traceId,
                            String internalToken) {

  public static final CallerContext ANONYMOUS = new CallerContext("", "", "", null, "");

  public static String orEmpty(String value) {
    return value == null ? "" : value;
  }
}

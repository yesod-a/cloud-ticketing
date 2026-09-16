package com.cloudticket.order;

import com.cloudticket.order.security.ScopeMatcher;
import java.util.Map;

/** Filters administrative order rows when an operator is limited to session scopes. */
public final class OrderScopeFilter {
  private OrderScopeFilter() {}

  public static boolean visible(Map<String, Object> order, String permissions, String scopes) {
    return ScopeMatcher.allows(permissions, scopes, "SESSION", String.valueOf(order.getOrDefault("sessionId", "")));
  }
}

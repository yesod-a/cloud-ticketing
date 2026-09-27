package com.cloudticket.common.web;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Envelope used by the endpoints that wrap their payload in {@code code/message/traceId/data}.
 *
 * <p>Four controllers built this map by hand; the shape lives here once.
 */
public final class ApiResponse {

  private ApiResponse() {}

  public static Map<String, Object> ok(String message, String traceId, Object data) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("code", "OK");
    value.put("message", message);
    value.put("traceId", traceId == null ? "" : traceId);
    value.put("data", data);
    return value;
  }

  public static Map<String, Object> ok(String message, Object data) {
    return ok(message, "", data);
  }

  public static Map<String, Object> code(String code) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("code", code);
    return value;
  }
}

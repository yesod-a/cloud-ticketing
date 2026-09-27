package com.cloudticket.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Copies the gateway identity headers into a {@link CallerContext} for the current request and
 * publishes the trace id to the logging context.
 *
 * <p>The trace id reaches {@code MDC} here, which is what the outbox writers persist, so a request
 * that produces an event can be followed end to end.
 */
public class CallerContextFilter extends OncePerRequestFilter {

  public static final String PERMISSIONS_HEADER = "X-User-Permissions";
  public static final String SCOPES_HEADER = "X-User-Scopes";
  public static final String USER_HEADER = "X-User-Id";
  public static final String TRACE_HEADER = "X-Trace-Id";
  public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";
  public static final String TRACE_MDC_KEY = "traceId";

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String traceId = resolveTraceId(request.getHeader(TRACE_HEADER));
    CallerContext previous = CallerContextHolder.current();
    CallerContextHolder.set(new CallerContext(
        header(request, PERMISSIONS_HEADER),
        header(request, SCOPES_HEADER),
        header(request, USER_HEADER),
        traceId,
        header(request, INTERNAL_TOKEN_HEADER)));
    if (!response.containsHeader(TRACE_HEADER)) response.setHeader(TRACE_HEADER, traceId);
    MDC.put(TRACE_MDC_KEY, traceId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(TRACE_MDC_KEY);
      CallerContextHolder.set(previous);
    }
  }

  private static String resolveTraceId(String supplied) {
    return supplied != null && !supplied.isBlank() && supplied.length() <= 64
        && supplied.matches("[A-Za-z0-9._:-]+")
        ? supplied
        : UUID.randomUUID().toString();
  }

  private static String header(HttpServletRequest request, String name) {
    String value = request.getHeader(name);
    return value == null ? "" : value;
  }
}

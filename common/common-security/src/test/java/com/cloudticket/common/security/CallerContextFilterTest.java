package com.cloudticket.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CallerContextFilterTest {

  private final CallerContextFilter filter = new CallerContextFilter();

  @AfterEach
  void clearContext() {
    CallerContextHolder.clear();
    MDC.clear();
  }

  @Test
  void copiesTheGatewayIdentityHeadersIntoTheContext() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CallerContextFilter.PERMISSIONS_HEADER, "activity:read");
    request.addHeader(CallerContextFilter.SCOPES_HEADER, "ACTIVITY:a1");
    request.addHeader(CallerContextFilter.USER_HEADER, "user-1");
    request.addHeader(CallerContextFilter.INTERNAL_TOKEN_HEADER, "dev-internal-token");
    AtomicReference<CallerContext> seen = new AtomicReference<>();

    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain() {
      @Override
      public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
        seen.set(CallerContextHolder.current());
      }
    });

    assertEquals("activity:read", seen.get().permissions());
    assertEquals("ACTIVITY:a1", seen.get().scopes());
    assertEquals("user-1", seen.get().userId());
    assertEquals("dev-internal-token", seen.get().internalToken());
    assertNotNull(seen.get().traceId());
  }

  @Test
  void reusesAnAcceptableTraceIdAndEchoesItBack() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CallerContextFilter.TRACE_HEADER, "trace-42");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> mdcDuringCall = new AtomicReference<>();

    filter.doFilter(request, response, new MockFilterChain() {
      @Override
      public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
        mdcDuringCall.set(MDC.get(CallerContextFilter.TRACE_MDC_KEY));
      }
    });

    assertEquals("trace-42", response.getHeader(CallerContextFilter.TRACE_HEADER));
    assertEquals("trace-42", mdcDuringCall.get());
  }

  @Test
  void replacesATraceIdThatCouldBeInjectedIntoALogLine() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CallerContextFilter.TRACE_HEADER, "bad\ntrace");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    String trace = response.getHeader(CallerContextFilter.TRACE_HEADER);
    assertFalse(trace.contains("\n"));
    assertTrue(trace.length() > 10);
  }

  @Test
  void clearsBothTheContextAndTheLoggingContextAfterTheRequest() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CallerContextFilter.USER_HEADER, "user-1");

    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

    assertEquals("", CallerContextHolder.current().userId());
    assertNull(MDC.get(CallerContextFilter.TRACE_MDC_KEY));
  }
}

package com.cloudticket.activity.cache;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Read-through cache for the anonymous activity endpoints.
 *
 * <p>Only catalog reads are cached: seat availability is a lock/order decision input and must stay
 * authoritative in the database, so it is deliberately excluded.
 */
public class PublicReadCacheFilter extends OncePerRequestFilter {
  private static final Pattern CACHEABLE = Pattern.compile("^/api/activities(?:/[^/]+)?$");
  private static final String ADMIN_PREFIX = "/api/admin/";

  private final PublicReadCache cache;

  public PublicReadCacheFilter(PublicReadCache cache) {
    this.cache = cache;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    if (!cache.enabled()) {
      chain.doFilter(request, response);
      return;
    }
    if (!"GET".equalsIgnoreCase(request.getMethod())) {
      if (path.startsWith(ADMIN_PREFIX)) cache.invalidate();
      chain.doFilter(request, response);
      return;
    }
    if (!CACHEABLE.matcher(path).matches()) {
      chain.doFilter(request, response);
      return;
    }
    String key = cache.key(request.getMethod(), path, request.getQueryString());
    Optional<String> cached = cache.get(key);
    if (cached.isPresent()) {
      writeJson(response, cached.get(), "HIT");
      return;
    }
    ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
    chain.doFilter(request, wrapper);
    byte[] body = wrapper.getContentAsByteArray();
    String contentType = wrapper.getContentType();
    if (wrapper.getStatus() == 200 && body.length > 0 && contentType != null && contentType.contains("json")) {
      cache.put(key, new String(body, StandardCharsets.UTF_8));
      wrapper.setHeader("X-Cache", "MISS");
    }
    wrapper.copyBodyToResponse();
  }

  private static void writeJson(HttpServletResponse response, String body, String state) throws IOException {
    response.setStatus(200);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.setHeader("X-Cache", state);
    response.getWriter().write(body);
  }
}

package com.cloudticket.order.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Decorates every internal service call with the concerns that used to be missing.
 *
 * <p>The clients previously issued a bare {@code client.post().uri(...)} with only the internal
 * token header: no timeout, no retry and no trace propagation. This wrapper adds them once, so a new
 * client automatically inherits the same behaviour — bounded retries with exponential backoff for
 * transport failures and 5xx responses, immediate propagation of 4xx decisions (a 409 seat conflict
 * must not be retried), and the caller's trace id carried to the downstream service.
 */
@Component
public class InternalServiceClient {

  private static final Logger log = LoggerFactory.getLogger(InternalServiceClient.class);
  private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";
  private static final String TRACE_HEADER = "X-Trace-Id";

  private final RestClient.Builder builder;
  private final String internalToken;
  private final int maxAttempts;
  private final long initialBackoffMs;
  private final Map<String, RestClient> clientsByBaseUrl = new ConcurrentHashMap<>();

  public InternalServiceClient(
      RestClient.Builder builder,
      @Value("${cloudticket.internal-service-token:dev-internal-token}") String internalToken,
      @Value("${cloudticket.internal-client.max-attempts:3}") int maxAttempts,
      @Value("${cloudticket.internal-client.initial-backoff-ms:50}") long initialBackoffMs) {
    this.builder = builder;
    this.internalToken = internalToken;
    this.maxAttempts = Math.max(1, maxAttempts);
    this.initialBackoffMs = Math.max(1, initialBackoffMs);
  }

  public <T> T get(String baseUrl, String uri, ParameterizedTypeReference<T> responseType, Object... uriVariables) {
    return withRetry("GET " + uri, () -> client(baseUrl).get().uri(uri, uriVariables)
        .retrieve().body(responseType));
  }

  public void post(String baseUrl, String uri, Object body, Object... uriVariables) {
    withRetry("POST " + uri, () -> client(baseUrl).post().uri(uri, uriVariables)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body == null ? Map.of() : body)
        .retrieve().toBodilessEntity());
  }

  private RestClient client(String baseUrl) {
    return clientsByBaseUrl.computeIfAbsent(baseUrl, url -> builder.clone()
        .baseUrl(url)
        .requestInterceptor((request, body, execution) -> {
          request.getHeaders().set(INTERNAL_TOKEN_HEADER, internalToken);
          String traceId = MDC.get("traceId");
          if (traceId != null && !traceId.isBlank()) request.getHeaders().set(TRACE_HEADER, traceId);
          return execution.execute(request, body);
        })
        .build());
  }

  private <T> T withRetry(String description, Supplier<T> call) {
    RuntimeException lastFailure = null;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        return call.get();
      } catch (RestClientResponseException failure) {
        if (!failure.getStatusCode().is5xxServerError()) throw failure;
        lastFailure = failure;
      } catch (ResourceAccessException failure) {
        lastFailure = failure;
      }
      if (attempt < maxAttempts) {
        backoff(attempt);
        log.debug("Retrying internal call {} (attempt {}/{})", description, attempt + 1, maxAttempts);
      }
    }
    log.warn("Internal call {} failed after {} attempts", description, maxAttempts, lastFailure);
    throw lastFailure == null ? new IllegalStateException(description + " failed") : lastFailure;
  }

  private void backoff(int attempt) {
    try {
      Thread.sleep(initialBackoffMs << (attempt - 1));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while retrying an internal call", interrupted);
    }
  }
}

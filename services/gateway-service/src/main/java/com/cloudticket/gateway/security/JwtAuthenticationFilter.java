package com.cloudticket.gateway.security;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
  private final AnonymousPathPolicy anonymous = new AnonymousPathPolicy();
  private final JwtTokenVerifier verifier;
  private final WebClient authClient;
  private final String internalToken;
  public JwtAuthenticationFilter(@Value("${cloudticket.auth.jwt-signing-key:dev-only-change-me}") String key, WebClient.Builder webClient, @Value("${cloudticket.auth-service-base-url:lb://auth-service}") String authBaseUrl, @Value("${cloudticket.internal-service-token:dev-internal-token}") String internalToken) { verifier = new JwtTokenVerifier(key); this.authClient = webClient.baseUrl(authBaseUrl).build(); this.internalToken = internalToken; }
  @Override public int getOrder() { return -100; }
  @Override public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();
    ServerHttpRequest cleaned = stripIdentityHeaders(exchange.getRequest());
    if (path.startsWith("/internal/")) return unauthorized(exchange);
    if (anonymous.allows(cleaned.getMethod().name(), path)) return chain.filter(exchange.mutate().request(cleaned).build());
    String authorization = cleaned.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (authorization == null || !authorization.startsWith("Bearer ")) return unauthorized(exchange);
    try {
      JwtTokenVerifier.Claims claims = verifier.verify(authorization.substring(7));
      ServerHttpRequest trusted = cleaned.mutate().headers(headers -> {
        headers.set("X-User-Id", claims.subject()); headers.set("X-User-Roles", claims.roles());
        headers.set("X-User-Permissions", claims.permissions()); headers.set("X-User-Scopes", claims.scopes()); headers.set("X-Scope-Version", claims.scopeVersion());
      }).build();
      return authClient.get().uri(uri -> uri.path("/api/internal/auth/token/status").queryParam("userId", claims.subject()).queryParam("scopeVersion", claims.scopeVersion()).queryParam("jti", claims.jti()).build()).header("X-Internal-Service-Token", internalToken).retrieve().bodyToMono(Status.class).flatMap(status -> status.active() ? chain.filter(exchange.mutate().request(trusted).build()) : unauthorized(exchange)).onErrorResume(error -> unauthorized(exchange));
    } catch (SecurityException ex) { return unauthorized(exchange); }
  }
  private ServerHttpRequest stripIdentityHeaders(ServerHttpRequest request) { return request.mutate().headers(h -> { h.remove("X-User-Id"); h.remove("X-User-Roles"); h.remove("X-User-Permissions"); h.remove("X-User-Scopes"); h.remove("X-Scope-Version"); h.remove("X-Internal-Service-Token"); }).build(); }
  private Mono<Void> unauthorized(ServerWebExchange exchange) { exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED); exchange.getResponse().getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json"); byte[] body = "{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\",\"traceId\":\"\",\"data\":null}".getBytes(StandardCharsets.UTF_8); return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body))); }
  private record Status(boolean active) {}
}

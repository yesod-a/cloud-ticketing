package com.cloudticket.common.web;

import java.util.UUID;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public class ReactiveTraceIdWebFilter implements WebFilter {
    @Override public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String supplied = exchange.getRequest().getHeaders().getFirst(TraceIdFilter.HEADER);
        String traceId = supplied != null && supplied.length() <= 64 && supplied.matches("[A-Za-z0-9._:-]+")
                ? supplied : UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set(TraceIdFilter.HEADER, traceId);
        return chain.filter(exchange);
    }
}

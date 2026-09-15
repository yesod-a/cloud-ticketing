package com.cloudticket.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfiguration {
  @Bean RouteLocator cloudTicketRoutes(RouteLocatorBuilder routes) {
    return routes.routes().route("auth-service", r -> r.path("/api/auth/**").uri("lb://auth-service"))
      .route("activity-service", r -> r.path("/api/activities/**", "/api/sessions/**", "/api/admin/activities/**", "/api/admin/venues/**", "/api/admin/sessions/**").uri("lb://activity-service"))
      .route("order-service", r -> r.path("/api/orders/**", "/api/me/orders/**", "/api/admin/orders/**").uri("lb://order-service"))
      .route("inventory-service", r -> r.path("/api/admin/inventory/**").uri("lb://inventory-service")).build();
  }
}

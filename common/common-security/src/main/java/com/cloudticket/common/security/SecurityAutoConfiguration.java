package com.cloudticket.common.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Registers the authorization aspect and the caller-context filter for every servlet service that
 * depends on {@code common-security}.
 */
@AutoConfiguration
@ConditionalOnClass(OncePerRequestFilter.class)
public class SecurityAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AuthorizationAspect authorizationAspect(
      @Value("${cloudticket.internal-service-token:dev-internal-token}") String internalServiceToken,
      ObjectProvider<AuditSink> auditSinks,
      ApplicationContext applicationContext) {
    return new AuthorizationAspect(internalServiceToken, auditSinks, applicationContext);
  }

  @Bean
  @ConditionalOnMissingBean
  public FilterRegistrationBean<CallerContextFilter> callerContextFilter() {
    FilterRegistrationBean<CallerContextFilter> registration =
        new FilterRegistrationBean<>(new CallerContextFilter());
    registration.addUrlPatterns("/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }
}

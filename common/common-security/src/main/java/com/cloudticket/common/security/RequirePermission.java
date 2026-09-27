package com.cloudticket.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requires the caller to hold at least one of the listed permissions.
 *
 * <p>{@code system:config} always satisfies the check, matching the behaviour the controllers used
 * to implement by hand before every mutating endpoint.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
  String[] value();
}

package com.cloudticket.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requires the caller's resource scope to cover the addressed resource.
 *
 * <p>Both expressions are evaluated as SpEL against the intercepted method arguments, so a method
 * parameter ({@code #id}) or a lookup bean ({@code @seatScopes.activity(#id)}) can supply the value.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireScope {

  /** Resource type such as {@code ACTIVITY}, {@code VENUE} or {@code SESSION}. */
  String type();

  /** Expression resolving the addressed resource id. */
  String id() default "#id";

  /** Optional expression resolving the owning activity id for child resources. */
  String parent() default "";
}

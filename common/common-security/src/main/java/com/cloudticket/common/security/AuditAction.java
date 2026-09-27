package com.cloudticket.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Records an audit entry once the annotated method returns successfully.
 *
 * <p>The expressions are SpEL: {@code #id} reads a method parameter, {@code #result} reads the
 * return value, and {@code @bean.method(#id)} calls a lookup bean.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditAction {

  /** Action code persisted in the audit log, for example {@code SESSION_CREATED}. */
  String action();

  /** Resource type persisted in the audit log, for example {@code SESSION}. */
  String resourceType();

  /** Expression resolving the audited resource id. */
  String resourceId() default "#id";

  /** Expression rendering the state before the call; empty renders {@code null}. */
  String before() default "";

  /** Expression rendering the state after the call. */
  String after() default "#result";

  /** Expression rendering the operator's justification, for rules that demand one. */
  String reason() default "";

  /**
   * Optional expression that must resolve to {@code true} for the entry to be written.
   *
   * <p>An endpoint that is idempotent - re-granting a role that is already granted, for example -
   * should not add a second audit line for a change that did not happen.
   */
  String when() default "";
}

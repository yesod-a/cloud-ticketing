package com.cloudticket.common.security;

import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * Enforces {@link RequireInternalToken}, {@link RequirePermission}, {@link RequireScope} and records
 * {@link AuditAction} entries.
 *
 * <p>Authorisation used to be hand-written in every controller method, which meant a new endpoint
 * silently shipped without a check when the author forgot one line. Declaring the requirement next
 * to the mapping makes the check the default and keeps the controller focused on the use case.
 */
@Aspect
public class AuthorizationAspect {

  private static final Logger log = LoggerFactory.getLogger(AuthorizationAspect.class);
  private static final String SYSTEM_CONFIG = "system:config";
  private static final String RESULT_VARIABLE = "result";

  private final String expectedInternalToken;
  private final ObjectProvider<AuditSink> auditSinks;
  private final BeanResolver beanResolver;
  private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();
  private final ExpressionParser expressions = new SpelExpressionParser();

  public AuthorizationAspect(String expectedInternalToken, ObjectProvider<AuditSink> auditSinks,
                             ApplicationContext applicationContext) {
    this.expectedInternalToken = expectedInternalToken;
    this.auditSinks = auditSinks;
    this.beanResolver = new BeanFactoryResolver(applicationContext);
  }

  @Around("@annotation(com.cloudticket.common.security.RequireInternalToken)"
      + " || @within(com.cloudticket.common.security.RequireInternalToken)"
      + " || @annotation(com.cloudticket.common.security.RequirePermission)"
      + " || @annotation(com.cloudticket.common.security.RequireScope)"
      + " || @annotation(com.cloudticket.common.security.AuditAction)")
  public Object guard(ProceedingJoinPoint joinPoint) throws Throwable {
    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    CallerContext caller = CallerContextHolder.current();

    if (requiresInternalToken(method)) requireInternalToken(caller);
    RequirePermission permission = method.getAnnotation(RequirePermission.class);
    if (permission != null) requirePermission(caller, permission);

    StandardEvaluationContext context = evaluationContext(joinPoint, method);
    RequireScope scope = method.getAnnotation(RequireScope.class);
    if (scope != null) requireScope(caller, scope, context);

    AuditAction audit = method.getAnnotation(AuditAction.class);
    if (audit == null) return joinPoint.proceed();

    // "before" must be read while the old state is still there; "resourceId"/"after" may reference
    // the return value, so they are evaluated once the call has produced it.
    String before = render(context, audit.before());
    Object result = joinPoint.proceed();
    context.setVariable(RESULT_VARIABLE, result);
    if (!shouldRecord(context, audit)) return result;
    record(audit, caller, render(context, audit.resourceId()), before, render(context, audit.after()),
        render(context, audit.reason()));
    return result;
  }

  private boolean shouldRecord(StandardEvaluationContext context, AuditAction audit) {
    String condition = audit.when();
    if (condition == null || condition.isBlank()) return true;
    Object value = expressions.parseExpression(condition).getValue(context, Boolean.class);
    return Boolean.TRUE.equals(value);
  }

  private void requireInternalToken(CallerContext caller) {
    if (!expectedInternalToken.equals(caller.internalToken())) {
      throw new SecurityException("internal authentication required");
    }
  }

  private void requirePermission(CallerContext caller, RequirePermission requirement) {
    if (ResourceScopeRule.contains(caller.permissions(), SYSTEM_CONFIG)) return;
    for (String wanted : requirement.value()) {
      if (ResourceScopeRule.contains(caller.permissions(), wanted)) return;
    }
    throw new SecurityException("forbidden");
  }

  private void requireScope(CallerContext caller, RequireScope requirement, StandardEvaluationContext context) {
    String resourceId = render(context, requirement.id());
    String parentId = render(context, requirement.parent());
    if (!ResourceScopeRule.allows(caller.permissions(), caller.scopes(), requirement.type(), resourceId, parentId)) {
      throw new SecurityException("forbidden");
    }
  }

  private void record(AuditAction audit, CallerContext caller, String resourceId, String before, String after,
                      String reason) {
    AuditSink sink = auditSinks.getIfAvailable();
    if (sink == null) return;
    try {
      sink.record(new AuditEntry(CallerContext.orEmpty(caller.userId()), audit.action(), audit.resourceType(),
          resourceId, before, after, caller.traceId(), reason));
    } catch (RuntimeException failure) {
      // An audit write must never turn a completed business action into an error response.
      log.warn("Unable to persist audit action {} for {} {}", audit.action(), audit.resourceType(), resourceId,
          failure);
    }
  }

  private StandardEvaluationContext evaluationContext(ProceedingJoinPoint joinPoint, Method method) {
    StandardEvaluationContext context = new StandardEvaluationContext();
    context.setBeanResolver(beanResolver);
    Object[] arguments = joinPoint.getArgs();
    String[] names = parameterNames.getParameterNames(method);
    for (int index = 0; index < arguments.length; index++) {
      context.setVariable("p" + index, arguments[index]);
      if (names != null && index < names.length && names[index] != null) {
        context.setVariable(names[index], arguments[index]);
      }
    }
    return context;
  }

  private String render(StandardEvaluationContext context, String expression) {
    if (expression == null || expression.isBlank()) return null;
    Object value = expressions.parseExpression(expression).getValue(context);
    return value == null ? null : String.valueOf(value);
  }

  /** A controller may declare the internal-token requirement once for every endpoint it exposes. */
  private static boolean requiresInternalToken(Method method) {
    return method.isAnnotationPresent(RequireInternalToken.class)
        || method.getDeclaringClass().isAnnotationPresent(RequireInternalToken.class);
  }
}

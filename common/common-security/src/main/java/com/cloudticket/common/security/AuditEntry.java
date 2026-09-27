package com.cloudticket.common.security;

/**
 * One audit record produced by {@link AuditAction}.
 *
 * @param reason operator justification, for the entries whose rules require one; may be {@code null}
 */
public record AuditEntry(String actor, String action, String resourceType, String resourceId,
                         String before, String after, String traceId, String reason) {

  public AuditEntry(String actor, String action, String resourceType, String resourceId,
                    String before, String after, String traceId) {
    this(actor, action, resourceType, resourceId, before, after, traceId, null);
  }
}

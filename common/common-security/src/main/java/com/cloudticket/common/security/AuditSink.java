package com.cloudticket.common.security;

/**
 * Persistence hook for audit records.
 *
 * <p>Each service stores audits in its own table, so the aspect stays storage agnostic and a
 * service only has to expose one bean.
 */
@FunctionalInterface
public interface AuditSink {
  void record(AuditEntry entry);
}

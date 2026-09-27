package com.cloudticket.auth.persistence;

import com.cloudticket.auth.persistence.entity.AuthAuditLogEntity;
import com.cloudticket.auth.persistence.mapper.AuthAuditLogMapper;
import com.cloudticket.common.security.AuditEntry;
import com.cloudticket.common.security.AuditSink;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Writes {@code auth_audit_log}; also the audit sink used by the authorization aspect.
 *
 * <p>The JSON columns hold quoted strings, which is what the SQL version produced with
 * {@code JSON_QUOTE}; serialising the value keeps the column valid JSON.
 */
@Repository
public class AuthAuditRepository implements AuditSink {

  private final AuthAuditLogMapper audits;
  private final ObjectMapper json;

  public AuthAuditRepository(AuthAuditLogMapper audits, ObjectMapper json) {
    this.audits = audits;
    this.json = json;
  }

  @Override
  public void record(AuditEntry entry) {
    AuthAuditLogEntity entity = new AuthAuditLogEntity();
    entity.setId(UUID.randomUUID());
    entity.setActorUserId(uuid(entry.actor()));
    entity.setAction(entry.action());
    entity.setResourceType(entry.resourceType());
    entity.setResourceId(uuid(entry.resourceId()));
    entity.setBeforeJson(quoted(entry.before()));
    entity.setAfterJson(quoted(entry.after()));
    entity.setTraceId(blankToNull(entry.traceId()));
    audits.insert(entity);
  }

  private static UUID uuid(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return UUID.fromString(value.trim());
    } catch (IllegalArgumentException notAUuid) {
      return null;
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private String quoted(String value) {
    if (value == null) return null;
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException impossible) {
      throw new IllegalStateException("unable to encode an audit value", impossible);
    }
  }
}

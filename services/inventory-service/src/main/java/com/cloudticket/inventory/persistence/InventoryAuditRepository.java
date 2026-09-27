package com.cloudticket.inventory.persistence;

import com.cloudticket.common.security.AuditEntry;
import com.cloudticket.common.security.AuditSink;
import com.cloudticket.inventory.persistence.entity.InventoryAuditLogEntity;
import com.cloudticket.inventory.persistence.mapper.InventoryAuditLogMapper;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Persistence for {@code inventory_audit_log}; also the audit sink used by the authorization aspect. */
@Repository
public class InventoryAuditRepository implements AuditSink {

  private final InventoryAuditLogMapper audits;

  public InventoryAuditRepository(InventoryAuditLogMapper audits) {
    this.audits = audits;
  }

  @Override
  public void record(AuditEntry entry) {
    InventoryAuditLogEntity entity = new InventoryAuditLogEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setActorUserId(Text.trimmedOrNull(entry.actor()));
    entity.setAction(entry.action());
    entity.setResourceId(entry.resourceId());
    entity.setBeforeStatus(entry.before());
    entity.setAfterStatus(Text.orEmpty(entry.after()));
    entity.setReason(Text.orEmpty(entry.reason()));
    entity.setTraceId(entry.traceId());
    audits.insert(entity);
  }
}

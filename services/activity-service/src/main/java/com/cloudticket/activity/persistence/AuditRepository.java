package com.cloudticket.activity.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudticket.activity.domain.Audit;
import com.cloudticket.activity.persistence.entity.ActivityAuditLogEntity;
import com.cloudticket.activity.persistence.mapper.ActivityAuditLogMapper;
import com.cloudticket.common.security.AuditEntry;
import com.cloudticket.common.security.AuditSink;
import com.cloudticket.common.web.PageResult;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Persistence for {@code activity_audit_log}; also the audit sink used by the authorization aspect. */
@Repository
public class AuditRepository implements AuditSink {

  private final ActivityAuditLogMapper audits;

  public AuditRepository(ActivityAuditLogMapper audits) {
    this.audits = audits;
  }

  @Override
  public void record(AuditEntry entry) {
    ActivityAuditLogEntity entity = new ActivityAuditLogEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setActorUserId(Text.orEmpty(entry.actor()));
    entity.setAction(entry.action());
    entity.setResourceType(entry.resourceType());
    entity.setResourceId(entry.resourceId());
    entity.setBeforeJson(entry.before());
    entity.setAfterJson(entry.after());
    entity.setTraceId(entry.traceId());
    audits.insert(entity);
  }

  public PageResult<Audit> page(String action, int page, int size) {
    int safePage = PageResult.safePage(page);
    int safeSize = PageResult.safeSize(size);
    String cleanAction = Text.orEmpty(action).trim();
    Page<ActivityAuditLogEntity> result = audits.selectPage(new Page<>(safePage + 1L, safeSize),
        Wrappers.<ActivityAuditLogEntity>lambdaQuery()
            .eq(!cleanAction.isBlank(), ActivityAuditLogEntity::getAction, cleanAction)
            .orderByDesc(ActivityAuditLogEntity::getCreatedAt));
    return new PageResult<>(result.getRecords().stream().map(AuditRepository::toDomain).toList(),
        safePage, safeSize, result.getTotal());
  }

  private static Audit toDomain(ActivityAuditLogEntity entity) {
    return new Audit(entity.getId(), entity.getActorUserId(), entity.getAction(), entity.getResourceType(),
        entity.getResourceId(), entity.getTraceId(),
        entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString());
  }
}

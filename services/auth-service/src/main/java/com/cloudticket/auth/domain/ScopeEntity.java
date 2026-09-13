package com.cloudticket.auth.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("auth_scope")
public record ScopeEntity(@Id UUID id, @Column("resource_type") String resourceType,
        @Column("resource_id") UUID resourceId, String status, @Column("created_at") Instant createdAt) {}

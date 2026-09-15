package com.cloudticket.auth.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("auth_role")
public record RoleEntity(@Id UUID id, String code, String name, boolean builtIn,
        String status, @Column("created_at") Instant createdAt, @Column("updated_at") Instant updatedAt) {}

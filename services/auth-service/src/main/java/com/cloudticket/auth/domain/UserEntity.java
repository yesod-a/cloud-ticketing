package com.cloudticket.auth.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("auth_user")
public record UserEntity(@Id UUID id, String phone, String email,
        @Column("password_hash") String passwordHash, String nickname, String status,
        @Column("failed_login_count") int failedLoginCount,
        @Column("locked_until") Instant lockedUntil,
        @Column("scope_version") long scopeVersion,
        @Column("created_at") Instant createdAt, @Column("updated_at") Instant updatedAt) {}

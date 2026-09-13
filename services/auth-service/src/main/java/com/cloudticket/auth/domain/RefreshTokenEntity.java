package com.cloudticket.auth.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("auth_refresh_token")
public record RefreshTokenEntity(@Id UUID id, @Column("user_id") UUID userId,
        @Column("token_hash") String tokenHash, @Column("family_id") UUID familyId,
        @Column("expires_at") Instant expiresAt, @Column("revoked_at") Instant revokedAt,
        @Column("replaced_by_id") UUID replacedById, @Column("created_at") Instant createdAt) {}

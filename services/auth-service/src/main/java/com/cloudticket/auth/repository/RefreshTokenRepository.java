package com.cloudticket.auth.repository;

import com.cloudticket.auth.domain.RefreshTokenEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends CrudRepository<RefreshTokenEntity, UUID> {
    @Modifying
    @Query("INSERT INTO auth_refresh_token (id, user_id, token_hash, family_id, expires_at, revoked_at, replaced_by_id, created_at) VALUES (UUID_TO_BIN(:id), UUID_TO_BIN(:userId), :tokenHash, UUID_TO_BIN(:familyId), :expiresAt, :revokedAt, UUID_TO_BIN(:replacedById), :createdAt)")
    void insert(@Param("id") String id, @Param("userId") String userId, @Param("tokenHash") String tokenHash,
                @Param("familyId") String familyId, @Param("expiresAt") Instant expiresAt,
                @Param("revokedAt") Instant revokedAt, @Param("replacedById") String replacedById,
                @Param("createdAt") Instant createdAt);
    @Query("SELECT * FROM auth_refresh_token WHERE token_hash = :tokenHash AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP LIMIT 1")
    Optional<RefreshTokenEntity> findActiveByHash(@Param("tokenHash") String tokenHash);
    @Query("SELECT * FROM auth_refresh_token WHERE token_hash = :tokenHash LIMIT 1")
    Optional<RefreshTokenEntity> findByHash(@Param("tokenHash") String tokenHash);
    @Query("UPDATE auth_refresh_token SET revoked_at = :revokedAt WHERE id = :id")
    @Modifying
    boolean revoke(@Param("id") UUID id, @Param("revokedAt") Instant revokedAt);
    @Query("UPDATE auth_refresh_token SET revoked_at = :revokedAt WHERE family_id = :familyId AND revoked_at IS NULL")
    @Modifying
    boolean revokeFamily(@Param("familyId") UUID familyId, @Param("revokedAt") Instant revokedAt);
}

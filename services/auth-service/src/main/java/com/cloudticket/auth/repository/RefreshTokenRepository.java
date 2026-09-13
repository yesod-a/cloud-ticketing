package com.cloudticket.auth.repository;

import com.cloudticket.auth.domain.RefreshTokenEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.repository.CrudRepository;

public interface RefreshTokenRepository extends CrudRepository<RefreshTokenEntity, UUID> {
    @Query("SELECT * FROM auth_refresh_token WHERE token_hash = :tokenHash AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP LIMIT 1")
    Optional<RefreshTokenEntity> findActiveByHash(String tokenHash);
    @Query("UPDATE auth_refresh_token SET revoked_at = :revokedAt WHERE id = :id")
    @Modifying
    boolean revoke(UUID id, Instant revokedAt);
}

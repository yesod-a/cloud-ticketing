package com.cloudticket.auth.repository;

import com.cloudticket.auth.domain.ScopeEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface ScopeRepository extends CrudRepository<ScopeEntity, UUID> {
    @Query("SELECT s.* FROM auth_scope s JOIN auth_user_scope us ON us.scope_id = s.id WHERE us.user_id = :userId AND s.status = 'ACTIVE'")
    List<ScopeEntity> findScopesForUser(UUID userId);
}

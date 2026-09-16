package com.cloudticket.auth.repository;

import com.cloudticket.auth.domain.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface UserRepository extends CrudRepository<UserEntity, UUID> {
    @Query("SELECT r.code FROM auth_role r JOIN auth_user_role ur ON ur.role_id=r.id WHERE ur.user_id=UUID_TO_BIN(:userId)")
    List<String> findRoleCodes(@Param("userId") String userId);
    @Query("SELECT p.code FROM auth_permission p JOIN auth_role_permission rp ON rp.permission_id=p.id JOIN auth_user_role ur ON ur.role_id=rp.role_id WHERE ur.user_id=UUID_TO_BIN(:userId)")
    List<String> findPermissionCodes(@Param("userId") String userId);
    @Query("SELECT CONCAT(s.resource_type, ':', BIN_TO_UUID(s.resource_id)) FROM auth_scope s JOIN auth_user_scope us ON us.scope_id=s.id WHERE us.user_id=UUID_TO_BIN(:userId) AND s.status='ACTIVE' ORDER BY s.resource_type,s.resource_id")
    List<String> findScopes(@Param("userId") String userId);
    @Modifying
    @Query("INSERT INTO auth_user (id, phone, email, password_hash, nickname, status, failed_login_count, locked_until, scope_version, created_at, updated_at) VALUES (UUID_TO_BIN(:id), :phone, :email, :passwordHash, :nickname, :status, :failedLoginCount, :lockedUntil, :scopeVersion, :createdAt, :updatedAt)")
    void insert(@Param("id") String id, @Param("phone") String phone, @Param("email") String email,
                @Param("passwordHash") String passwordHash, @Param("nickname") String nickname,
                @Param("status") String status, @Param("failedLoginCount") int failedLoginCount,
                @Param("lockedUntil") java.time.Instant lockedUntil, @Param("scopeVersion") long scopeVersion,
                @Param("createdAt") java.time.Instant createdAt, @Param("updatedAt") java.time.Instant updatedAt);
    Optional<UserEntity> findByPhoneOrEmail(String phone, String email);
    default Optional<UserEntity> findByPhoneOrEmail(String identifier) { return findByPhoneOrEmail(identifier, identifier); }
    boolean existsByPhone(String phone);
    boolean existsByEmail(String email);
}

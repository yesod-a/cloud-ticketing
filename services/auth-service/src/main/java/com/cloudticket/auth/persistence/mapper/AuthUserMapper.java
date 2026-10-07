package com.cloudticket.auth.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudticket.auth.persistence.entity.AuthUserEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * {@code auth_user} plus the role, permission and scope lookups the JWT is built from.
 *
 * <p>State changes that must be able to clear a column — unlocking an account, clearing the failed
 * counter — are explicit statements, because a field-level update would silently skip {@code null}.
 */
public interface AuthUserMapper extends BaseMapper<AuthUserEntity> {

  @Select("SELECT r.code FROM auth_role r JOIN auth_user_role ur ON ur.role_id = r.id WHERE ur.user_id = #{userId}")
  List<String> findRoleCodes(@Param("userId") UUID userId);

  @Select("SELECT p.code FROM auth_permission p JOIN auth_role_permission rp ON rp.permission_id = p.id "
      + "JOIN auth_user_role ur ON ur.role_id = rp.role_id WHERE ur.user_id = #{userId}")
  List<String> findPermissionCodes(@Param("userId") UUID userId);

  @Select("SELECT CONCAT(s.resource_type, ':', COALESCE(BIN_TO_UUID(s.resource_id), '')) FROM auth_scope s "
      + "JOIN auth_user_scope us ON us.scope_id = s.id "
      + "WHERE us.user_id = #{userId} AND s.status = 'ACTIVE' ORDER BY s.resource_type,s.resource_id")
  List<String> findScopes(@Param("userId") UUID userId);

  @Select("SELECT * FROM auth_user WHERE phone = #{identifier} OR email = #{identifier} LIMIT 1")
  AuthUserEntity findByIdentifier(@Param("identifier") String identifier);

  @Select("SELECT * FROM auth_user WHERE id = #{id}")
  AuthUserEntity selectProfile(@Param("id") UUID id);

  @Select("<script>SELECT id,nickname,avatar_filename,created_at,updated_at FROM auth_user "
      + "WHERE status='ACTIVE' AND id IN "
      + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
  List<AuthUserEntity> selectPublicProfiles(@Param("ids") List<UUID> ids);

  @Update("UPDATE auth_user SET nickname = #{nickname} WHERE id = #{id}")
  int updateNickname(@Param("id") UUID id, @Param("nickname") String nickname);

  @Update("UPDATE auth_user SET avatar_filename = #{filename} WHERE id = #{id}")
  int updateAvatarFilename(@Param("id") UUID id, @Param("filename") String filename);

  @Select("SELECT COUNT(*) FROM auth_user WHERE phone = #{phone}")
  int countByPhone(@Param("phone") String phone);

  @Select("SELECT COUNT(*) FROM auth_user WHERE email = #{email}")
  int countByEmail(@Param("email") String email);

  @Select("SELECT id FROM auth_user WHERE phone = #{phone}")
  List<UUID> findIdsByPhone(@Param("phone") String phone);

  @Select("SELECT id,phone,email,nickname,status,created_at FROM auth_user "
      + "WHERE (#{keyword} = '' OR phone LIKE CONCAT('%', #{keyword}, '%') "
      + "OR email LIKE CONCAT('%', #{keyword}, '%') OR nickname LIKE CONCAT('%', #{keyword}, '%')) "
      + "AND (#{status} = '' OR status = #{status}) ORDER BY created_at DESC")
  Page<AuthUserEntity> selectAdminPage(Page<AuthUserEntity> page, @Param("keyword") String keyword,
                                       @Param("status") String status);

  @Update("UPDATE auth_user SET failed_login_count = #{failedLoginCount}, locked_until = #{lockedUntil} "
      + "WHERE id = #{id}")
  int updateLoginState(@Param("id") UUID id, @Param("failedLoginCount") int failedLoginCount,
                       @Param("lockedUntil") Instant lockedUntil);

  @Update("UPDATE auth_user SET password_hash = #{passwordHash}, failed_login_count = 0, "
      + "locked_until = NULL, scope_version = scope_version + 1 WHERE id = #{id}")
  int resetPassword(@Param("id") UUID id, @Param("passwordHash") String passwordHash);

  @Update("UPDATE auth_user SET status = #{status}, scope_version = scope_version + 1 WHERE id = #{id}")
  int changeStatus(@Param("id") UUID id, @Param("status") String status);

  @Update("UPDATE auth_user SET password_hash = #{passwordHash}, status = 'ACTIVE', failed_login_count = 0, "
      + "locked_until = NULL WHERE id = #{id}")
  int reactivate(@Param("id") UUID id, @Param("passwordHash") String passwordHash);

  @Update("UPDATE auth_user SET scope_version = scope_version + 1 WHERE id = #{id}")
  int bumpScopeVersion(@Param("id") UUID id);

  @Insert("INSERT IGNORE INTO auth_user_role(user_id, role_id) SELECT #{userId}, id FROM auth_role "
      + "WHERE code = #{roleCode}")
  int grantRole(@Param("userId") UUID userId, @Param("roleCode") String roleCode);

}

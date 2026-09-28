package com.cloudticket.auth;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudticket.auth.persistence.mapper.AuthRefreshTokenMapper;
import com.cloudticket.auth.persistence.mapper.AuthUserMapper;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class AuthSchemaTest {

  @Test
  void migrationDefinesAuthTablesAndUniqueContactConstraints() throws Exception {
    var resource = getClass().getResourceAsStream("/db/migration/V1__auth_schema.sql");
    assertNotNull(resource);
    var sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
    assertTrue(sql.contains("CREATE TABLE auth_user"));
    assertTrue(Pattern.compile("UNIQUE\\s+KEY\\s+uq_auth_user_phone", Pattern.CASE_INSENSITIVE).matcher(sql).find());
    assertTrue(Pattern.compile("UNIQUE\\s+KEY\\s+uq_auth_user_email", Pattern.CASE_INSENSITIVE).matcher(sql).find());
    for (var table : new String[] {"auth_user", "auth_role", "auth_permission", "auth_user_role",
            "auth_role_permission", "auth_scope", "auth_user_scope", "auth_refresh_token",
            "auth_login_log", "auth_audit_log"}) {
      assertTrue(sql.contains("CREATE TABLE " + table), "missing table " + table);
    }
    for (var role : new String[] {"USER", "OPERATOR", "ORDER_ADMIN", "INVENTORY_ADMIN", "AUDITOR", "SUPER_ADMIN"}) {
      assertTrue(sql.contains("'" + role + "'"), "missing role " + role);
    }
    for (var permission : new String[] {"activity:read", "activity:write", "activity:publish", "venue:read",
            "venue:write", "session:write", "seat-layout:read", "seat-layout:write", "inventory:read",
            "inventory:lock-release", "inventory:adjust", "order:read", "order:cancel", "order:refund",
            "order:export", "user:read", "user:manage", "role:manage", "scope:manage", "audit:read", "system:config"}) {
      assertTrue(sql.contains("'" + permission + "'"), "missing permission " + permission);
    }
    assertTrue(sql.contains("FOREIGN KEY (replaced_by_id) REFERENCES auth_refresh_token(id)"));
    assertTrue(sql.contains("CREATE TRIGGER auth_audit_log_no_update"));
    assertTrue(sql.contains("CREATE TRIGGER auth_audit_log_no_delete"));
    assertTrue(sql.contains("SIGNAL SQLSTATE '45000'"));
  }

  @Test
  void refreshTokenRevocationIsDeclaredAsAnUpdateStatement() throws Exception {
    var method = AuthRefreshTokenMapper.class.getMethod("revoke", java.util.UUID.class, java.time.Instant.class);

    Update update = method.getAnnotation(Update.class);
    assertNotNull(update, "revoking a refresh token must be a modifying mapper statement");
    assertTrue(update.value()[0].contains("revoked_at"));
  }

  @Test
  void profileMigrationAndExplicitProfileMapperStatementsExist() {
    var resource = getClass().getResourceAsStream("/db/migration/V4__user_profile.sql");
    assertNotNull(resource, "profile migration must be available to Flyway");
    try (resource) {
      var sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(sql.contains("avatar_filename VARCHAR(255) NULL"));
    } catch (Exception failure) {
      throw new AssertionError(failure);
    }

    assertTrue(java.util.Arrays.stream(com.cloudticket.auth.persistence.entity.AuthUserEntity.class
        .getDeclaredFields()).anyMatch(field -> field.getName().equals("avatarFilename")));
    assertTrue(java.util.Arrays.stream(AuthUserMapper.class.getMethods())
        .anyMatch(method -> method.getName().equals("selectProfile")
            && method.isAnnotationPresent(Select.class)));
    assertProfileUpdate("updateNickname", "nickname = #{nickname}");
    assertProfileUpdate("updateAvatarFilename", "avatar_filename = #{filename}");
  }

  private static void assertProfileUpdate(String methodName, String expectedSql) {
    var method = java.util.Arrays.stream(AuthUserMapper.class.getMethods())
        .filter(candidate -> candidate.getName().equals(methodName)).findFirst();
    assertTrue(method.isPresent(), "missing mapper method " + methodName);
    var update = method.get().getAnnotation(Update.class);
    assertNotNull(update, methodName + " must be an explicit update statement");
    assertTrue(update.value()[0].contains(expectedSql));
    assertFalse(update.value()[0].contains("password_hash"));
  }
}

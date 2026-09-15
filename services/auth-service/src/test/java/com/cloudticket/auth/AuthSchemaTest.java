package com.cloudticket.auth;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import com.cloudticket.auth.repository.RefreshTokenRepository;
import org.springframework.data.jdbc.repository.query.Modifying;
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
    void refreshTokenRevokeIsDeclaredAsModifyingQuery() throws Exception {
        var method = RefreshTokenRepository.class.getMethod("revoke", java.util.UUID.class, java.time.Instant.class);
        assertNotNull(method.getAnnotation(Modifying.class));
    }
}

package com.cloudticket.auth;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
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
        assertTrue(sql.contains("auth_refresh_token"));
        assertTrue(sql.contains("auth_audit_log"));
    }
}

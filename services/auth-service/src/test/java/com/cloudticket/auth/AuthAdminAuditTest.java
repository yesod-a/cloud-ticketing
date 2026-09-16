package com.cloudticket.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.cloudticket.auth.api.AuthAdminController;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AuthAdminAuditTest {
  @Test
  void roleGrantIncrementsScopeVersionAndWritesAuditAfterSuccessfulGrant() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    AuthAdminController controller = new AuthAdminController(jdbc);
    UUID userId = UUID.randomUUID();

    var response = controller.role(userId.toString(), Map.of("roleCode", "OPERATOR"),
        "role:manage", "actor-1", "trace-1");

    assertEquals(userId.toString(), response.getBody().get("id"));
    verify(jdbc).update(eq("UPDATE auth_user SET scope_version=scope_version+1 WHERE id=UUID_TO_BIN(?)"), eq(userId.toString()));
    verify(jdbc).update(startsWith("INSERT INTO auth_audit_log"), any(Object[].class));
  }

  @Test
  void roleGrantIsForbiddenWithoutRoleManagePermission() {
    AuthAdminController controller = new AuthAdminController(mock(JdbcTemplate.class));
    var response = controller.role(UUID.randomUUID().toString(), Map.of("roleCode", "OPERATOR"),
        "user:read", "actor-1", "trace-1");
    assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, response.getStatusCode());
  }

  @Test
  void duplicateRoleGrantDoesNotBumpScopeVersionOrWriteAudit() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
    AuthAdminController controller = new AuthAdminController(jdbc);
    controller.role(UUID.randomUUID().toString(), Map.of("roleCode", "OPERATOR"),
        "role:manage", "actor-1", "trace-1");
    verify(jdbc, never()).update(eq("UPDATE auth_user SET scope_version=scope_version+1 WHERE id=UUID_TO_BIN(?)"), (Object[]) any());
    verify(jdbc, never()).update(startsWith("INSERT INTO auth_audit_log"), any(Object[].class));
  }
}

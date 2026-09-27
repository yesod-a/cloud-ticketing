package com.cloudticket.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.auth.api.AuthAdminController;
import com.cloudticket.auth.api.AuthCommands;
import com.cloudticket.auth.persistence.entity.AuthRoleEntity;
import com.cloudticket.auth.persistence.entity.AuthUserEntity;
import com.cloudticket.auth.persistence.mapper.AuthAuditLogMapper;
import com.cloudticket.auth.persistence.mapper.AuthRoleMapper;
import com.cloudticket.auth.persistence.mapper.AuthUserMapper;
import com.cloudticket.common.security.AuditEntry;
import com.cloudticket.common.security.AuditSink;
import com.cloudticket.common.security.CallerContext;
import com.cloudticket.common.security.CallerContextHolder;
import com.cloudticket.common.web.PageResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class AuthAdminControllerTest {

  private final AuthUserMapper users = mock(AuthUserMapper.class);
  private final AuthRoleMapper roles = mock(AuthRoleMapper.class);
  private final AuthAuditLogMapper audits = mock(AuthAuditLogMapper.class);
  private final AuditSink auditSink = mock(AuditSink.class);

  private AuthAdminController controller() {
    return TestAspects.authorized(new AuthAdminController(users, roles, audits), auditSink);
  }

  @Test
  void roleGrantIncrementsScopeVersionAndWritesAuditAfterSuccessfulGrant() {
    UUID userId = UUID.randomUUID();
    when(users.grantRole(userId, "OPERATOR")).thenReturn(1);

    Map<String, Object> response = asCaller("role:manage",
        () -> controller().role(userId.toString(), new AuthCommands.GrantRole("OPERATOR")));

    assertEquals(userId.toString(), response.get("id"));
    assertEquals(true, response.get("changed"));
    verify(users).bumpScopeVersion(userId);
    verify(auditSink).record(new AuditEntry("actor-1", "USER_ROLE_GRANTED", "USER", userId.toString(),
        null, "OPERATOR", "trace-1", null));
  }

  @Test
  void duplicateRoleGrantDoesNotBumpScopeVersionOrWriteAudit() {
    UUID userId = UUID.randomUUID();
    when(users.grantRole(userId, "OPERATOR")).thenReturn(0);

    Map<String, Object> response = asCaller("role:manage",
        () -> controller().role(userId.toString(), new AuthCommands.GrantRole("OPERATOR")));

    assertEquals(false, response.get("changed"));
    verify(users, never()).bumpScopeVersion(any());
    verify(auditSink, never()).record(any());
  }

  @Test
  void roleGrantIsForbiddenWithoutRoleManagePermission() {
    assertThrows(SecurityException.class, () -> asCaller("user:read",
        () -> controller().role(UUID.randomUUID().toString(), new AuthCommands.GrantRole("OPERATOR"))));
    verify(users, never()).grantRole(any(), any());
  }

  @Test
  void statusChangeAuditsTheNewStatus() {
    UUID userId = UUID.randomUUID();

    asCaller("user:manage", () -> controller().status(userId.toString(),
        new AuthCommands.ChangeUserStatus("DISABLED")));

    verify(users).changeStatus(userId, "DISABLED");
    verify(auditSink).record(new AuditEntry("actor-1", "USER_STATUS_CHANGED", "USER", userId.toString(),
        null, "DISABLED", "trace-1", null));
  }

  @Test
  void statusChangeRejectsAnUnknownStatus() {
    assertThrows(IllegalArgumentException.class, () -> asCaller("user:manage",
        () -> controller().status(UUID.randomUUID().toString(), new AuthCommands.ChangeUserStatus("LOCKED"))));
  }

  @Test
  void userListingRequiresTheReadPermission() {
    assertThrows(SecurityException.class, () -> asCaller("role:manage",
        () -> controller().users("", "", 0, 20)));
    verify(users, never()).selectAdminPage(any(), any(), any());
  }

  @Test
  void userListingHidesInternalColumns() {
    AuthUserEntity user = new AuthUserEntity();
    user.setId(UUID.randomUUID());
    user.setPhone("13800138000");
    user.setStatus("ACTIVE");
    user.setPasswordHash("$2a$secret");
    when(users.selectAdminPage(any(), any(), any()))
        .thenAnswer(invocation -> {
          var page = invocation.<com.baomidou.mybatisplus.extension.plugins.pagination.Page<AuthUserEntity>>getArgument(0);
          page.setRecords(List.of(user));
          page.setTotal(1);
          return page;
        });

    Map<String, Object> response = asCaller("user:read", () -> controller().users("", "", 0, 20));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
    assertEquals(1, items.size());
    assertEquals("13800138000", items.get(0).get("phone"));
    assertEquals(false, items.get(0).containsKey("passwordHash"));
  }

  @Test
  void roleListingReturnsTheSharedShape() {
    AuthRoleEntity role = new AuthRoleEntity();
    role.setId(UUID.randomUUID());
    role.setCode("OPERATOR");
    role.setName("Operator");
    role.setStatus("ACTIVE");
    when(roles.selectAllOrdered()).thenReturn(List.of(role));

    Map<String, Object> response = asCaller("role:manage", () -> controller().roles());

    assertEquals(1, ((List<?>) response.get("items")).size());
  }

  private static <T> T asCaller(String permissions, Supplier<T> action) {
    return CallerContextHolder.scoped(
        new CallerContext(permissions, "", "actor-1", "trace-1", ""), action);
  }
}

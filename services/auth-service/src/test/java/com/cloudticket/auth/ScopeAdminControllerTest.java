package com.cloudticket.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cloudticket.auth.api.ScopeAdminController;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class ScopeAdminControllerTest {
  @Test
  void rejectsScopeCreationWithoutScopePermission() {
    ScopeAdminController controller = new ScopeAdminController(mock(JdbcTemplate.class));
    assertThrows(SecurityException.class, () -> controller.create(
        Map.of("resourceType", "ACTIVITY", "resourceId", "activity-1"), "activity:read", "admin", "trace"));
  }

  @Test
  void rejectsUnsupportedResourceType() {
    ScopeAdminController controller = new ScopeAdminController(mock(JdbcTemplate.class));
    assertThrows(IllegalArgumentException.class, () -> controller.create(
        Map.of("resourceType", "UNKNOWN", "resourceId", "resource-1"), "scope:manage", "admin", "trace"));
  }

  @Test
  void bindsUserAndBumpsScopeVersion() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(contains("auth_user"), eq(Integer.class), eq("user-1"))).thenReturn(1);
    when(jdbc.queryForObject(contains("auth_scope"), eq(Integer.class), eq("scope-1"))).thenReturn(1);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    ScopeAdminController controller = new ScopeAdminController(jdbc);

    Map<String, Object> result = controller.bind("scope-1", "user-1", "scope:manage", "admin", "trace");

    assertEquals("scope-1", result.get("scopeId"));
    verify(jdbc).update(contains("scope_version=scope_version+1"), eq("user-1"));
  }

  @Test
  void duplicateBindingDoesNotBumpVersionOrWriteAudit() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(contains("auth_user"), eq(Integer.class), eq("user-1"))).thenReturn(1);
    when(jdbc.queryForObject(contains("auth_scope"), eq(Integer.class), eq("scope-1"))).thenReturn(1);
    when(jdbc.update(startsWith("INSERT IGNORE"), any(Object[].class))).thenReturn(0);
    ScopeAdminController controller = new ScopeAdminController(jdbc);

    controller.bind("scope-1", "user-1", "scope:manage", "admin", "trace");

    verify(jdbc, never()).update(contains("scope_version=scope_version+1"), any(Object[].class));
    verify(jdbc, never()).update(contains("INSERT INTO auth_audit_log"), any(Object[].class));
  }

  @Test
  void rejectsBindingUnknownUserWithNotFound() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(contains("auth_user"), eq(Integer.class), eq("missing-user"))).thenReturn(0);
    ScopeAdminController controller = new ScopeAdminController(jdbc);

    var error = assertThrows(ResponseStatusException.class,
        () -> controller.bind("scope-1", "missing-user", "scope:manage", "admin", "trace"));

    assertEquals(404, error.getStatusCode().value());
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void rejectsBindingUnknownScopeWithNotFound() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(contains("auth_user"), eq(Integer.class), eq("user-1"))).thenReturn(1);
    when(jdbc.queryForObject(contains("auth_scope"), eq(Integer.class), eq("missing-scope"))).thenReturn(0);
    ScopeAdminController controller = new ScopeAdminController(jdbc);

    var error = assertThrows(ResponseStatusException.class,
        () -> controller.bind("missing-scope", "user-1", "scope:manage", "admin", "trace"));

    assertEquals(404, error.getStatusCode().value());
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void rejectsUnbindingUnknownUserWithNotFound() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(contains("auth_user"), eq(Integer.class), eq("missing-user"))).thenReturn(0);
    ScopeAdminController controller = new ScopeAdminController(jdbc);

    var error = assertThrows(ResponseStatusException.class,
        () -> controller.unbind("scope-1", "missing-user", "scope:manage", "admin", "trace"));

    assertEquals(404, error.getStatusCode().value());
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }
}

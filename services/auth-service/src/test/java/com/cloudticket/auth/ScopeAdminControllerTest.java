package com.cloudticket.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.auth.api.AuthCommands;
import com.cloudticket.auth.api.ScopeAdminController;
import com.cloudticket.auth.persistence.entity.AuthScopeEntity;
import com.cloudticket.auth.persistence.entity.AuthUserEntity;
import com.cloudticket.auth.persistence.mapper.AuthScopeMapper;
import com.cloudticket.auth.persistence.mapper.AuthUserMapper;
import com.cloudticket.common.security.AuditEntry;
import com.cloudticket.common.security.AuditSink;
import com.cloudticket.common.security.CallerContext;
import com.cloudticket.common.security.CallerContextHolder;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.server.ResponseStatusException;

class ScopeAdminControllerTest {

  private final AuthScopeMapper scopes = mock(AuthScopeMapper.class);
  private final AuthUserMapper users = mock(AuthUserMapper.class);
  private final AuditSink auditSink = mock(AuditSink.class);

  private ScopeAdminController controller() {
    return TestAspects.authorized(new ScopeAdminController(scopes, users), auditSink);
  }

  @Test
  void rejectsScopeCreationWithoutScopePermission() {
    assertThrows(SecurityException.class, () -> asCaller("activity:read",
        () -> controller().create(new AuthCommands.CreateScope("ACTIVITY", UUID.randomUUID().toString()))));
    verify(scopes, never()).insert(any(AuthScopeEntity.class));
  }

  @Test
  void rejectsUnsupportedResourceType() {
    assertThrows(IllegalArgumentException.class, () -> asCaller("scope:manage",
        () -> controller().create(new AuthCommands.CreateScope("UNKNOWN", UUID.randomUUID().toString()))));
  }

  @Test
  void scopeCreationAuditsTheNewGrant() {
    UUID resourceId = UUID.randomUUID();

    Map<String, Object> response = asCaller("scope:manage",
        () -> controller().create(new AuthCommands.CreateScope("ACTIVITY", resourceId.toString())));

    assertEquals("ACTIVITY", response.get("resourceType"));
    assertEquals(true, response.get("changed"));
    verify(auditSink).record(new AuditEntry("actor-1", "SCOPE_CREATED", "SCOPE",
        String.valueOf(response.get("id")), null, "ACTIVITY:" + resourceId, "trace-1", null));
  }

  @Test
  void duplicateScopeCreationReturnsTheExistingGrantWithoutAudit() {
    UUID resourceId = UUID.randomUUID();
    AuthScopeEntity existing = new AuthScopeEntity();
    existing.setId(UUID.randomUUID());
    existing.setResourceType("ACTIVITY");
    existing.setResourceId(resourceId);
    existing.setStatus("ACTIVE");
    when(scopes.insert(any(AuthScopeEntity.class))).thenThrow(new DuplicateKeyException("duplicate"));
    when(scopes.findByResource("ACTIVITY", resourceId)).thenReturn(existing);

    Map<String, Object> response = asCaller("scope:manage",
        () -> controller().create(new AuthCommands.CreateScope("ACTIVITY", resourceId.toString())));

    assertEquals(existing.getId().toString(), response.get("id"));
    assertEquals(false, response.get("changed"));
    verify(auditSink, never()).record(any());
  }

  @Test
  void bindsUserAndBumpsScopeVersion() {
    UUID scopeId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    existingScope(scopeId);
    existingUser(userId);
    when(scopes.bind(userId, scopeId)).thenReturn(1);

    Map<String, Object> result = asCaller("scope:manage",
        () -> controller().bind(scopeId.toString(), userId.toString()));

    assertEquals(scopeId.toString(), result.get("scopeId"));
    verify(scopes).bumpScopeVersion(userId);
    verify(auditSink).record(new AuditEntry("actor-1", "SCOPE_BOUND", "SCOPE", scopeId.toString(),
        null, userId.toString(), "trace-1", null));
  }

  @Test
  void duplicateBindingDoesNotBumpVersionOrWriteAudit() {
    UUID scopeId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    existingScope(scopeId);
    existingUser(userId);
    when(scopes.bind(userId, scopeId)).thenReturn(0);

    asCaller("scope:manage", () -> controller().bind(scopeId.toString(), userId.toString()));

    verify(scopes, never()).bumpScopeVersion(any());
    verify(auditSink, never()).record(any());
  }

  @Test
  void unbindingBumpsTheVersionAndAudits() {
    UUID scopeId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    existingScope(scopeId);
    existingUser(userId);
    when(scopes.unbind(userId, scopeId)).thenReturn(1);

    asCaller("scope:manage", () -> controller().unbind(scopeId.toString(), userId.toString()));

    verify(scopes).bumpScopeVersion(userId);
    verify(auditSink).record(new AuditEntry("actor-1", "SCOPE_UNBOUND", "SCOPE", scopeId.toString(),
        null, userId.toString(), "trace-1", null));
  }

  @Test
  void rejectsBindingUnknownUserWithNotFound() {
    UUID scopeId = UUID.randomUUID();
    existingScope(scopeId);

    var error = assertThrows(ResponseStatusException.class, () -> asCaller("scope:manage",
        () -> controller().bind(scopeId.toString(), UUID.randomUUID().toString())));

    assertEquals(404, error.getStatusCode().value());
    verify(scopes, never()).bind(any(), any());
  }

  @Test
  void rejectsBindingUnknownScopeWithNotFound() {
    UUID userId = UUID.randomUUID();
    existingUser(userId);

    var error = assertThrows(ResponseStatusException.class, () -> asCaller("scope:manage",
        () -> controller().bind(UUID.randomUUID().toString(), userId.toString())));

    assertEquals(404, error.getStatusCode().value());
    verify(scopes, never()).bind(any(), any());
  }

  @Test
  void rejectsAMalformedIdentifierWithNotFound() {
    var error = assertThrows(ResponseStatusException.class, () -> asCaller("scope:manage",
        () -> controller().bind("not-a-uuid", UUID.randomUUID().toString())));

    assertEquals(404, error.getStatusCode().value());
  }

  private void existingScope(UUID id) {
    AuthScopeEntity scope = new AuthScopeEntity();
    scope.setId(id);
    scope.setResourceType("ACTIVITY");
    scope.setResourceId(UUID.randomUUID());
    scope.setStatus("ACTIVE");
    when(scopes.selectById(id)).thenReturn(scope);
  }

  private void existingUser(UUID id) {
    AuthUserEntity user = new AuthUserEntity();
    user.setId(id);
    user.setStatus("ACTIVE");
    when(users.selectById(id)).thenReturn(user);
  }

  private static <T> T asCaller(String permissions, Supplier<T> action) {
    return CallerContextHolder.scoped(
        new CallerContext(permissions, "", "actor-1", "trace-1", ""), action);
  }
}

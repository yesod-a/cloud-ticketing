package com.cloudticket.auth.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudticket.auth.persistence.entity.AuthScopeEntity;
import com.cloudticket.auth.persistence.entity.AuthUserEntity;
import com.cloudticket.auth.persistence.mapper.AuthScopeMapper;
import com.cloudticket.auth.persistence.mapper.AuthUserMapper;
import com.cloudticket.common.security.AuditAction;
import com.cloudticket.common.security.RequirePermission;
import com.cloudticket.common.web.PageResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Administrative API for assigning resource scopes to operators. */
@RestController
@RequestMapping("/api/admin/auth/scopes")
public class ScopeAdminController {

  private static final Set<String> RESOURCE_TYPES = Set.of("ACTIVITY", "VENUE", "SESSION", "AREA");

  private final AuthScopeMapper scopes;
  private final AuthUserMapper users;

  public ScopeAdminController(AuthScopeMapper scopes, AuthUserMapper users) {
    this.scopes = scopes;
    this.users = users;
  }

  @RequirePermission("scope:manage")
  @GetMapping
  public Map<String, Object> list(@RequestParam(name = "resourceType", defaultValue = "") String resourceType,
                                  @RequestParam(name = "status", defaultValue = "") String status,
                                  @RequestParam(name = "page", defaultValue = "0") int page,
                                  @RequestParam(name = "size", defaultValue = "20") int size) {
    int safePage = PageResult.safePage(page);
    int safeSize = PageResult.safeSize(size);
    Page<AuthScopeEntity> result = scopes.selectAdminPage(new Page<>(safePage + 1L, safeSize),
        resourceType == null ? "" : resourceType.trim().toUpperCase(Locale.ROOT),
        status == null ? "" : status.trim().toUpperCase(Locale.ROOT));
    List<Map<String, Object>> items = result.getRecords().stream()
        .map(ScopeAdminController::scopeView)
        .toList();
    return new PageResult<>(items, safePage, safeSize, result.getTotal()).asMap();
  }

  @RequirePermission("scope:manage")
  @AuditAction(action = "SCOPE_CREATED", resourceType = "SCOPE", resourceId = "#result['id']",
      after = "#result['resourceType'] + ':' + #result['resourceId']", when = "#result['changed']")
  @PostMapping
  public Map<String, Object> create(@RequestBody AuthCommands.CreateScope body) {
    String type = normalizeType(body.resourceType());
    String resourceId = clean(body.resourceId());
    if (resourceId == null) throw new IllegalArgumentException("resourceId required");
    UUID resourceUuid = UUID.fromString(resourceId);

    AuthScopeEntity scope = new AuthScopeEntity();
    scope.setId(UUID.randomUUID());
    scope.setResourceType(type);
    scope.setResourceId(resourceUuid);
    scope.setStatus("ACTIVE");
    try {
      scopes.insert(scope);
    } catch (DuplicateKeyException duplicate) {
      AuthScopeEntity existing = scopes.findByResource(type, resourceUuid);
      if (existing == null) throw duplicate;
      return scopeResponse(existing, false);
    }
    return scopeResponse(scope, true);
  }

  @RequirePermission("scope:manage")
  @AuditAction(action = "SCOPE_BOUND", resourceType = "SCOPE", resourceId = "#scopeId",
      after = "#userId", when = "#result['changed']")
  @PutMapping("/{scopeId}/users/{userId}")
  public Map<String, Object> bind(@PathVariable("scopeId") String scopeId, @PathVariable("userId") String userId) {
    UUID scopeUuid = requireExistingScope(scopeId);
    UUID userUuid = requireExistingUser(userId);
    boolean changed = scopes.bind(userUuid, scopeUuid) > 0;
    if (changed) scopes.bumpScopeVersion(userUuid);
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("scopeId", scopeId);
    response.put("userId", userId);
    response.put("status", "BOUND");
    response.put("changed", changed);
    return response;
  }

  @RequirePermission("scope:manage")
  @AuditAction(action = "SCOPE_UNBOUND", resourceType = "SCOPE", resourceId = "#scopeId",
      after = "#userId", when = "#result['changed']")
  @DeleteMapping("/{scopeId}/users/{userId}")
  public Map<String, Object> unbind(@PathVariable("scopeId") String scopeId,
                                    @PathVariable("userId") String userId) {
    UUID scopeUuid = requireExistingScope(scopeId);
    UUID userUuid = requireExistingUser(userId);
    boolean changed = scopes.unbind(userUuid, scopeUuid) > 0;
    if (changed) scopes.bumpScopeVersion(userUuid);
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("scopeId", scopeId);
    response.put("userId", userId);
    response.put("status", "UNBOUND");
    response.put("changed", changed);
    return response;
  }

  private UUID requireExistingScope(String scopeId) {
    UUID id = parseUuid(scopeId);
    if (id == null || scopes.selectById(id) == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "scope not found: " + scopeId);
    }
    return id;
  }

  private UUID requireExistingUser(String userId) {
    UUID id = parseUuid(userId);
    AuthUserEntity user = id == null ? null : users.selectById(id);
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found: " + userId);
    }
    return id;
  }

  private static UUID parseUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (RuntimeException notAUuid) {
      return null;
    }
  }

  private static Map<String, Object> scopeResponse(AuthScopeEntity scope, boolean changed) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("id", scope.getId().toString());
    response.put("resourceType", scope.getResourceType());
    response.put("resourceId", scope.getResourceId() == null ? "" : scope.getResourceId().toString());
    response.put("status", scope.getStatus());
    response.put("changed", changed);
    return response;
  }

  private static Map<String, Object> scopeView(AuthScopeEntity scope) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", scope.getId() == null ? "" : scope.getId().toString());
    value.put("resourceType", scope.getResourceType());
    value.put("resourceId", scope.getResourceId() == null ? "" : scope.getResourceId().toString());
    value.put("status", scope.getStatus());
    value.put("userCount", scope.getUserCount() == null ? 0L : scope.getUserCount());
    value.put("createdAt", scope.getCreatedAt() == null ? null : scope.getCreatedAt().toString());
    return value;
  }

  private String normalizeType(String value) {
    String type = clean(value);
    if (type == null || !RESOURCE_TYPES.contains(type.toUpperCase(Locale.ROOT))) {
      throw new IllegalArgumentException("unsupported resourceType");
    }
    return type.toUpperCase(Locale.ROOT);
  }

  private static String clean(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }
}

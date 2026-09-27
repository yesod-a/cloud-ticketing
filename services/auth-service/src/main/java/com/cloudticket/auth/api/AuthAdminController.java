package com.cloudticket.auth.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudticket.auth.persistence.entity.AuthAuditLogEntity;
import com.cloudticket.auth.persistence.entity.AuthRoleEntity;
import com.cloudticket.auth.persistence.entity.AuthUserEntity;
import com.cloudticket.auth.persistence.mapper.AuthAuditLogMapper;
import com.cloudticket.auth.persistence.mapper.AuthRoleMapper;
import com.cloudticket.auth.persistence.mapper.AuthUserMapper;
import com.cloudticket.common.security.AuditAction;
import com.cloudticket.common.security.RequirePermission;
import com.cloudticket.common.web.PageResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** User, role and audit administration. */
@RestController
@RequestMapping("/api/admin/auth")
public class AuthAdminController {

  private static final Set<String> USER_STATUSES = Set.of("ACTIVE", "DISABLED");

  private final AuthUserMapper users;
  private final AuthRoleMapper roles;
  private final AuthAuditLogMapper audits;

  public AuthAdminController(AuthUserMapper users, AuthRoleMapper roles, AuthAuditLogMapper audits) {
    this.users = users;
    this.roles = roles;
    this.audits = audits;
  }

  @RequirePermission("user:read")
  @GetMapping("/users")
  public Map<String, Object> users(@RequestParam(name = "keyword", defaultValue = "") String keyword,
                                   @RequestParam(name = "status", defaultValue = "") String status,
                                   @RequestParam(name = "page", defaultValue = "0") int page,
                                   @RequestParam(name = "size", defaultValue = "20") int size) {
    int safePage = PageResult.safePage(page);
    int safeSize = PageResult.safeSize(size);
    Page<AuthUserEntity> result = users.selectAdminPage(new Page<>(safePage + 1L, safeSize),
        keyword.trim(), status.trim());
    List<Map<String, Object>> items = result.getRecords().stream().map(AuthAdminController::userView).toList();
    return new PageResult<>(items, safePage, safeSize, result.getTotal()).asMap();
  }

  @RequirePermission("user:manage")
  @AuditAction(action = "USER_STATUS_CHANGED", resourceType = "USER", resourceId = "#id",
      after = "#body.status()")
  @PutMapping("/users/{id}/status")
  public Map<String, Object> status(@PathVariable("id") String id,
                                    @RequestBody AuthCommands.ChangeUserStatus body) {
    String next = body.statusOrDefault();
    if (!USER_STATUSES.contains(next)) throw new IllegalArgumentException("invalid status");
    users.changeStatus(UUID.fromString(id), next);
    return Map.of("id", id, "status", next);
  }

  @RequirePermission("role:manage")
  @AuditAction(action = "USER_ROLE_GRANTED", resourceType = "USER", resourceId = "#id",
      after = "#body.roleCode()", when = "#result['changed']")
  @PutMapping("/users/{id}/role")
  public Map<String, Object> role(@PathVariable("id") String id, @RequestBody AuthCommands.GrantRole body) {
    String code = body.roleCode();
    if (code == null || code.isBlank()) throw new IllegalArgumentException("roleCode required");
    UUID userId = UUID.fromString(id);
    boolean changed = users.grantRole(userId, code.trim()) > 0;
    if (changed) users.bumpScopeVersion(userId);
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("id", id);
    response.put("roleCode", code);
    response.put("changed", changed);
    return response;
  }

  @RequirePermission("role:manage")
  @GetMapping("/roles")
  public Map<String, Object> roles() {
    return Map.of("items", roles.selectAllOrdered().stream().map(AuthAdminController::roleView).toList());
  }

  @RequirePermission("audit:read")
  @GetMapping("/audits")
  public Map<String, Object> audits(@RequestParam(name = "action", defaultValue = "") String action,
                                    @RequestParam(name = "page", defaultValue = "0") int page,
                                    @RequestParam(name = "size", defaultValue = "20") int size) {
    int safePage = PageResult.safePage(page);
    int safeSize = PageResult.safeSize(size);
    Page<AuthAuditLogEntity> result = audits.selectPageByAction(new Page<>(safePage + 1L, safeSize),
        action.trim());
    List<Map<String, Object>> items = result.getRecords().stream().map(AuthAdminController::auditView).toList();
    return new PageResult<>(items, safePage, safeSize, result.getTotal()).asMap();
  }

  private static Map<String, Object> userView(AuthUserEntity user) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", user.getId().toString());
    value.put("phone", orEmpty(user.getPhone()));
    value.put("email", orEmpty(user.getEmail()));
    value.put("nickname", orEmpty(user.getNickname()));
    value.put("status", user.getStatus());
    value.put("createdAt", user.getCreatedAt() == null ? null : user.getCreatedAt().toString());
    return value;
  }

  private static Map<String, Object> roleView(AuthRoleEntity role) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", role.getId() == null ? "" : role.getId().toString());
    value.put("code", role.getCode());
    value.put("name", role.getName());
    value.put("status", role.getStatus());
    return value;
  }

  private static Map<String, Object> auditView(AuthAuditLogEntity audit) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", audit.getId().toString());
    value.put("actor", audit.getActorUserId() == null ? "" : audit.getActorUserId().toString());
    value.put("action", audit.getAction());
    value.put("resourceType", audit.getResourceType());
    value.put("resourceId", audit.getResourceId() == null ? "" : audit.getResourceId().toString());
    value.put("traceId", orEmpty(audit.getTraceId()));
    value.put("createdAt", audit.getCreatedAt() == null ? null : audit.getCreatedAt().toString());
    return value;
  }

  private static String orEmpty(String value) {
    return value == null ? "" : value;
  }
}

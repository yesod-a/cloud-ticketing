package com.cloudticket.auth.api;

import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

/** Administrative API for assigning resource scopes to operators. */
@RestController
@RequestMapping("/api/admin/auth/scopes")
public class ScopeAdminController {
  private static final Set<String> RESOURCE_TYPES = Set.of("ACTIVITY", "VENUE", "SESSION", "AREA");
  private final JdbcTemplate jdbc;

  public ScopeAdminController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(name = "resourceType", defaultValue = "") String resourceType,
      @RequestParam(name = "status", defaultValue = "") String status,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size,
      @RequestHeader(value = "X-User-Permissions", defaultValue = "") String permissions) {
    require(permissions);
    int p = Math.max(0, page), s = Math.min(100, Math.max(1, size));
    String type = resourceType == null ? "" : resourceType.trim().toUpperCase(Locale.ROOT);
    String state = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    Integer total = jdbc.queryForObject(
        "SELECT COUNT(*) FROM auth_scope WHERE (?='' OR resource_type=?) AND (?='' OR status=?)",
        Integer.class, type, type, state, state);
    var items = jdbc.query(
        "SELECT BIN_TO_UUID(s.id) id,s.resource_type,COALESCE(BIN_TO_UUID(s.resource_id),'') resource_id,s.status,s.created_at,(SELECT COUNT(*) FROM auth_user_scope us WHERE us.scope_id=s.id) user_count "
            + "FROM auth_scope s WHERE (?='' OR s.resource_type=?) AND (?='' OR s.status=?) ORDER BY s.resource_type,s.resource_id LIMIT ? OFFSET ?",
        (r, n) -> Map.of("id", r.getString("id"), "resourceType", r.getString("resource_type"),
            "resourceId", Objects.toString(r.getString("resource_id"), ""), "status", r.getString("status"),
            "userCount", r.getInt("user_count"), "createdAt", r.getTimestamp("created_at").toInstant().toString()),
        type, type, state, state, s, p * s);
    return page(items, p, s, total == null ? 0 : total);
  }

  @PostMapping
  public Map<String, Object> create(@RequestBody Map<String, String> body,
      @RequestHeader(value = "X-User-Permissions", defaultValue = "") String permissions,
      @RequestHeader(value = "X-User-Id", defaultValue = "") String actor,
      @RequestHeader(value = "X-Trace-Id", defaultValue = "") String trace) {
    require(permissions);
    String type = normalizeType(body.get("resourceType"));
    String resourceId = clean(body.get("resourceId"));
    if (resourceId == null) throw new IllegalArgumentException("resourceId required");
    String id = UUID.randomUUID().toString();
    try {
      jdbc.update("INSERT INTO auth_scope(id,resource_type,resource_id,status) VALUES(UUID_TO_BIN(?),?,UUID_TO_BIN(?),'ACTIVE')", id, type, resourceId);
    } catch (DuplicateKeyException duplicate) {
      return findByResource(type, resourceId).orElseThrow(() -> duplicate);
    }
    audit(actor, "SCOPE_CREATED", id, type + ":" + resourceId, trace);
    return Map.of("id", id, "resourceType", type, "resourceId", resourceId, "status", "ACTIVE");
  }

  @PutMapping("/{scopeId}/users/{userId}")
  public Map<String, Object> bind(@PathVariable String scopeId, @PathVariable String userId,
      @RequestHeader(value = "X-User-Permissions", defaultValue = "") String permissions,
      @RequestHeader(value = "X-User-Id", defaultValue = "") String actor,
      @RequestHeader(value = "X-Trace-Id", defaultValue = "") String trace) {
    require(permissions);
    requireExisting("auth_user", userId, "user", userId);
    requireExisting("auth_scope", scopeId, "scope", scopeId);
    int inserted = jdbc.update("INSERT IGNORE INTO auth_user_scope(user_id,scope_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?))", userId, scopeId);
    if (inserted > 0) {
      jdbc.update("UPDATE auth_user SET scope_version=scope_version+1 WHERE id=UUID_TO_BIN(?)", userId);
      audit(actor, "SCOPE_BOUND", scopeId, userId, trace);
    }
    return Map.of("scopeId", scopeId, "userId", userId, "status", "BOUND");
  }

  private void requireExisting(String table, String id, String resourceType, String resourceId) {
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE id=UUID_TO_BIN(?)",
        Integer.class, id);
    if (count == null || count == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, resourceType + " not found: " + resourceId);
    }
  }

  @DeleteMapping("/{scopeId}/users/{userId}")
  public Map<String, Object> unbind(@PathVariable String scopeId, @PathVariable String userId,
      @RequestHeader(value = "X-User-Permissions", defaultValue = "") String permissions,
      @RequestHeader(value = "X-User-Id", defaultValue = "") String actor,
      @RequestHeader(value = "X-Trace-Id", defaultValue = "") String trace) {
    require(permissions);
    requireExisting("auth_user", userId, "user", userId);
    requireExisting("auth_scope", scopeId, "scope", scopeId);
    int deleted = jdbc.update("DELETE FROM auth_user_scope WHERE user_id=UUID_TO_BIN(?) AND scope_id=UUID_TO_BIN(?)", userId, scopeId);
    if (deleted > 0) {
      jdbc.update("UPDATE auth_user SET scope_version=scope_version+1 WHERE id=UUID_TO_BIN(?)", userId);
      audit(actor, "SCOPE_UNBOUND", scopeId, userId, trace);
    }
    return Map.of("scopeId", scopeId, "userId", userId, "status", "UNBOUND");
  }

  private void require(String permissions) {
    if (permissions == null || (!permissions.contains("scope:manage") && !permissions.contains("system:config"))) {
      throw new SecurityException("forbidden");
    }
  }

  private String normalizeType(String value) {
    String type = clean(value);
    if (type == null || !RESOURCE_TYPES.contains(type.toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("unsupported resourceType");
    return type.toUpperCase(Locale.ROOT);
  }

  private static String clean(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }

  private Optional<Map<String, Object>> findByResource(String type, String resourceId) {
    return jdbc.query("SELECT BIN_TO_UUID(id) id,resource_type,COALESCE(BIN_TO_UUID(resource_id),'') resource_id,status FROM auth_scope WHERE resource_type=? AND resource_id=UUID_TO_BIN(?)",
        (r, n) -> {
          Map<String, Object> value = new LinkedHashMap<>();
          value.put("id", r.getString("id"));
          value.put("resourceType", r.getString("resource_type"));
          value.put("resourceId", r.getString("resource_id"));
          value.put("status", r.getString("status"));
          return value;
        }, type, resourceId).stream().findFirst();
  }

  private void audit(String actor, String action, String resourceId, String after, String trace) {
    jdbc.update("INSERT INTO auth_audit_log(id,actor_user_id,action,resource_type,resource_id,after_json,trace_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(NULLIF(?,'')),?,?,UUID_TO_BIN(NULLIF(?,'')),JSON_QUOTE(?),?)",
        UUID.randomUUID().toString(), actor, action, "SCOPE", resourceId, after, trace);
  }

  private Map<String, Object> page(List<?> items, int page, int size, long total) {
    return Map.of("items", items, "page", page, "size", size, "total", total, "totalPages", total == 0 ? 0 : (total + size - 1) / size);
  }
}

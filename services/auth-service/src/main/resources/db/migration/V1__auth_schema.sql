CREATE TABLE auth_user (
  id BINARY(16) NOT NULL PRIMARY KEY,
  phone VARCHAR(32) NULL,
  email VARCHAR(320) NULL,
  password_hash VARCHAR(255) NOT NULL,
  nickname VARCHAR(120) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  failed_login_count INT NOT NULL DEFAULT 0,
  locked_until TIMESTAMP(6) NULL,
  scope_version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uq_auth_user_phone (phone), UNIQUE KEY uq_auth_user_email (email)
);
CREATE TABLE auth_role (id BINARY(16) NOT NULL PRIMARY KEY, code VARCHAR(64) NOT NULL UNIQUE, name VARCHAR(120) NOT NULL, built_in BOOLEAN NOT NULL DEFAULT FALSE, status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6));
CREATE TABLE auth_permission (id BINARY(16) NOT NULL PRIMARY KEY, code VARCHAR(128) NOT NULL UNIQUE, name VARCHAR(160) NOT NULL);
CREATE TABLE auth_user_role (user_id BINARY(16) NOT NULL, role_id BINARY(16) NOT NULL, PRIMARY KEY (user_id, role_id), FOREIGN KEY (user_id) REFERENCES auth_user(id), FOREIGN KEY (role_id) REFERENCES auth_role(id));
CREATE TABLE auth_role_permission (role_id BINARY(16) NOT NULL, permission_id BINARY(16) NOT NULL, PRIMARY KEY (role_id, permission_id), FOREIGN KEY (role_id) REFERENCES auth_role(id), FOREIGN KEY (permission_id) REFERENCES auth_permission(id));
CREATE TABLE auth_scope (id BINARY(16) NOT NULL PRIMARY KEY, resource_type VARCHAR(32) NOT NULL, resource_id BINARY(16) NULL, status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), UNIQUE KEY uq_auth_scope_resource (resource_type, resource_id));
CREATE TABLE auth_user_scope (user_id BINARY(16) NOT NULL, scope_id BINARY(16) NOT NULL, PRIMARY KEY (user_id, scope_id), FOREIGN KEY (user_id) REFERENCES auth_user(id), FOREIGN KEY (scope_id) REFERENCES auth_scope(id));
CREATE TABLE auth_refresh_token (id BINARY(16) NOT NULL PRIMARY KEY, user_id BINARY(16) NOT NULL, token_hash CHAR(64) NOT NULL UNIQUE, family_id BINARY(16) NOT NULL, expires_at TIMESTAMP(6) NOT NULL, revoked_at TIMESTAMP(6) NULL, replaced_by_id BINARY(16) NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), FOREIGN KEY (user_id) REFERENCES auth_user(id));
CREATE TABLE auth_login_log (id BINARY(16) NOT NULL PRIMARY KEY, user_id BINARY(16) NULL, identifier VARCHAR(320) NULL, success BOOLEAN NOT NULL, ip_address VARCHAR(64) NULL, user_agent VARCHAR(512) NULL, reason VARCHAR(255) NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6));
CREATE TABLE auth_audit_log (id BINARY(16) NOT NULL PRIMARY KEY, actor_user_id BINARY(16) NULL, action VARCHAR(128) NOT NULL, resource_type VARCHAR(64) NOT NULL, resource_id BINARY(16) NULL, before_json JSON NULL, after_json JSON NULL, trace_id VARCHAR(128) NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6));

INSERT INTO auth_role (id, code, name, built_in) VALUES
(UUID_TO_BIN(UUID()), 'USER', 'User', TRUE), (UUID_TO_BIN(UUID()), 'OPERATOR', 'Operator', TRUE), (UUID_TO_BIN(UUID()), 'ORDER_ADMIN', 'Order administrator', TRUE), (UUID_TO_BIN(UUID()), 'INVENTORY_ADMIN', 'Inventory administrator', TRUE), (UUID_TO_BIN(UUID()), 'AUDITOR', 'Auditor', TRUE), (UUID_TO_BIN(UUID()), 'SUPER_ADMIN', 'Super administrator', TRUE);
INSERT INTO auth_permission (id, code, name) VALUES
(UUID_TO_BIN(UUID()), 'activity:read', 'Read activities'), (UUID_TO_BIN(UUID()), 'activity:write', 'Write activities'), (UUID_TO_BIN(UUID()), 'activity:publish', 'Publish activities'), (UUID_TO_BIN(UUID()), 'venue:read', 'Read venues'), (UUID_TO_BIN(UUID()), 'venue:write', 'Write venues'), (UUID_TO_BIN(UUID()), 'session:write', 'Write sessions'), (UUID_TO_BIN(UUID()), 'seat-layout:read', 'Read seat layouts'), (UUID_TO_BIN(UUID()), 'seat-layout:write', 'Write seat layouts'), (UUID_TO_BIN(UUID()), 'inventory:read', 'Read inventory'), (UUID_TO_BIN(UUID()), 'inventory:lock-release', 'Release inventory locks'), (UUID_TO_BIN(UUID()), 'inventory:adjust', 'Adjust inventory'), (UUID_TO_BIN(UUID()), 'order:read', 'Read orders'), (UUID_TO_BIN(UUID()), 'order:cancel', 'Cancel orders'), (UUID_TO_BIN(UUID()), 'order:refund', 'Refund orders'), (UUID_TO_BIN(UUID()), 'order:export', 'Export orders'), (UUID_TO_BIN(UUID()), 'user:read', 'Read users'), (UUID_TO_BIN(UUID()), 'user:manage', 'Manage users'), (UUID_TO_BIN(UUID()), 'role:manage', 'Manage roles'), (UUID_TO_BIN(UUID()), 'scope:manage', 'Manage scopes'), (UUID_TO_BIN(UUID()), 'audit:read', 'Read audit logs'), (UUID_TO_BIN(UUID()), 'system:config', 'Configure system');

INSERT IGNORE INTO auth_permission (id, code, name) VALUES
(UUID_TO_BIN(UUID()), 'promotion:read', 'Read promotions'),
(UUID_TO_BIN(UUID()), 'promotion:write', 'Manage promotions'),
(UUID_TO_BIN(UUID()), 'promotion:publish', 'Publish promotions'),
(UUID_TO_BIN(UUID()), 'comment:read', 'Read comments'),
(UUID_TO_BIN(UUID()), 'comment:moderate', 'Moderate comments'),
(UUID_TO_BIN(UUID()), 'comment:like', 'Like comments');
INSERT IGNORE INTO auth_role_permission (role_id, permission_id)
SELECT r.id,p.id FROM auth_role r JOIN auth_permission p
 ON r.code='SUPER_ADMIN' AND p.code IN ('promotion:read','promotion:write','promotion:publish','comment:read','comment:moderate','comment:like');

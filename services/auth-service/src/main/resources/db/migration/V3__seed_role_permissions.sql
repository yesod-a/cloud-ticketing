-- Built-in roles receive the least privilege set needed by the admin console.
INSERT IGNORE INTO auth_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM auth_role r JOIN auth_permission p
  ON (r.code='OPERATOR' AND p.code IN ('activity:read','activity:write','activity:publish','venue:read','venue:write','session:write','seat-layout:read','seat-layout:write'))
  OR (r.code='ORDER_ADMIN' AND p.code IN ('order:read','order:cancel','order:refund','order:export'))
  OR (r.code='INVENTORY_ADMIN' AND p.code IN ('inventory:read','inventory:adjust','inventory:lock-release'))
  OR (r.code='AUDITOR' AND p.code='audit:read');

ALTER TABLE inventory_lock MODIFY status VARCHAR(20) NOT NULL DEFAULT 'PREPARED';
CREATE INDEX idx_inventory_lock_status_expiry ON inventory_lock(status, expires_at, active);

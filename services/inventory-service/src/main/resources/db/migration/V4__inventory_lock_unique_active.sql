ALTER TABLE inventory_lock DROP INDEX uq_inventory_active_lock;
ALTER TABLE inventory_lock ADD COLUMN active_seat_key CHAR(80) GENERATED ALWAYS AS (CASE WHEN active = 1 THEN CONCAT(session_id, ':', seat_id) ELSE NULL END) STORED;
ALTER TABLE inventory_lock ADD UNIQUE KEY uq_inventory_active_lock (active_seat_key);

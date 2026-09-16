ALTER TABLE inventory_seat ADD COLUMN activity_id CHAR(36) NULL;
UPDATE inventory_seat SET activity_id='0cdff6f9-6521-4ecb-99aa-0e277e0bd54c' WHERE session_id='20000000-0000-0000-0000-000000000001';
UPDATE inventory_seat SET activity_id='930b75a5-daa4-477c-8290-ccff1b2ba910' WHERE session_id='20000000-0000-0000-0000-000000000002';
CREATE INDEX idx_inventory_seat_activity ON inventory_seat(activity_id);

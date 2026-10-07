ALTER TABLE ticket_order ADD COLUMN activity_id CHAR(36) NULL;
ALTER TABLE ticket_order ADD COLUMN original_amount_minor INT NOT NULL DEFAULT 0;
ALTER TABLE ticket_order ADD COLUMN discount_amount_minor INT NOT NULL DEFAULT 0;
ALTER TABLE ticket_order ADD COLUMN coupon_id CHAR(36) NULL;
ALTER TABLE ticket_order ADD COLUMN coupon_reservation_id CHAR(36) NULL;
CREATE INDEX idx_ticket_order_activity_status ON ticket_order(activity_id,status);

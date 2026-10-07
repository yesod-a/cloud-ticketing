CREATE TABLE IF NOT EXISTS promotion_coupon (
  id CHAR(36) PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  discount_type VARCHAR(32) NOT NULL,
  threshold_amount_minor INT NOT NULL DEFAULT 0,
  discount_value INT NOT NULL,
  max_discount_minor INT NULL,
  total_count INT NOT NULL,
  issued_count INT NOT NULL DEFAULT 0,
  used_count INT NOT NULL DEFAULT 0,
  user_limit INT NOT NULL DEFAULT 1,
  issue_begin_at TIMESTAMP NOT NULL,
  issue_end_at TIMESTAMP NOT NULL,
  term_begin_at TIMESTAMP NULL,
  term_end_at TIMESTAMP NULL,
  term_days INT NULL,
  status VARCHAR(20) NOT NULL,
  created_by CHAR(36) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_coupon_status_window(status, issue_begin_at, issue_end_at)
);

CREATE TABLE IF NOT EXISTS promotion_coupon_scope (
  coupon_id CHAR(36) NOT NULL,
  resource_type VARCHAR(20) NOT NULL,
  resource_id CHAR(36) NOT NULL DEFAULT '',
  PRIMARY KEY(coupon_id, resource_type, resource_id),
  CONSTRAINT fk_coupon_scope_coupon FOREIGN KEY(coupon_id) REFERENCES promotion_coupon(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS promotion_user_coupon (
  id CHAR(36) PRIMARY KEY,
  coupon_id CHAR(36) NOT NULL,
  user_id CHAR(36) NOT NULL,
  status VARCHAR(20) NOT NULL,
  term_begin_at TIMESTAMP NULL,
  term_end_at TIMESTAMP NULL,
  held_order_id CHAR(36) NULL,
  held_at TIMESTAMP NULL,
  used_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_coupon(coupon_id, user_id),
  INDEX idx_user_coupon_status(user_id, status, term_end_at),
  CONSTRAINT fk_user_coupon_coupon FOREIGN KEY(coupon_id) REFERENCES promotion_coupon(id)
);

CREATE TABLE IF NOT EXISTS promotion_coupon_reservation (
  reservation_id CHAR(36) PRIMARY KEY,
  order_id CHAR(36) NOT NULL,
  user_coupon_id CHAR(36) NOT NULL,
  coupon_id CHAR(36) NOT NULL,
  user_id CHAR(36) NOT NULL,
  original_amount_minor INT NOT NULL,
  discount_amount_minor INT NOT NULL,
  payable_amount_minor INT NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_reservation_order(order_id),
  INDEX idx_reservation_status(status, updated_at)
);

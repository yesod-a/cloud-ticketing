CREATE TABLE IF NOT EXISTS inventory_cache_warmup (
  session_id CHAR(36) PRIMARY KEY,
  preheat_at TIMESTAMP NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  cache_version VARCHAR(64) NULL,
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMP NULL,
  last_error VARCHAR(512) NULL,
  claimed_by VARCHAR(128) NULL,
  claim_until TIMESTAMP NULL,
  preheated_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX ix_inventory_cache_warmup_due(status, preheat_at, next_attempt_at)
);

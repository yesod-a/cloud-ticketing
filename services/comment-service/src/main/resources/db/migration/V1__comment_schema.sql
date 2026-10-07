CREATE TABLE IF NOT EXISTS comment (
 id CHAR(36) PRIMARY KEY, activity_id CHAR(36) NOT NULL, user_id CHAR(36) NOT NULL, parent_id CHAR(36) NULL,
 content VARCHAR(1000) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'VISIBLE', like_count INT NOT NULL DEFAULT 0,
 reply_count INT NOT NULL DEFAULT 0, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 INDEX idx_comment_activity_status(activity_id,status,created_at), INDEX idx_comment_parent_status(parent_id,status)
);
CREATE TABLE IF NOT EXISTS comment_like_record (
 comment_id CHAR(36) NOT NULL, user_id CHAR(36) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(comment_id,user_id)
);

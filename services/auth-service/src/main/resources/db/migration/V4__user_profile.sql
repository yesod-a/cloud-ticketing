ALTER TABLE auth_user
  ADD COLUMN avatar_filename VARCHAR(255) NULL AFTER nickname;

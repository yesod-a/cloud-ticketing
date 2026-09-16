package com.cloudticket.auth;

import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the optional local administrator only when both admin environment values are provided. */
@Component
public class AdminAccountInitializer implements CommandLineRunner {
  private final JdbcTemplate jdbc;
  private final PasswordEncoder encoder;
  public AdminAccountInitializer(JdbcTemplate jdbc, PasswordEncoder encoder) { this.jdbc = jdbc; this.encoder = encoder; }
  @Override public void run(String... args) {
    String phone = env("AUTH_ADMIN_PHONE"); String password = env("AUTH_ADMIN_PASSWORD");
    if (phone == null || password == null) return;
    String normalized = phone.replaceAll("[^0-9+]", "");
    if (normalized.isBlank() || password.length() < 8) throw new IllegalStateException("AUTH_ADMIN_PHONE and a valid AUTH_ADMIN_PASSWORD are required");
    UUID id = jdbc.query("SELECT id FROM auth_user WHERE phone=?", rs -> rs.next() ? UuidConverters.toUuid(rs.getBytes(1)) : null, normalized);
    if (id == null) {
      id = UUID.randomUUID(); Instant now = Instant.now();
      jdbc.update("INSERT INTO auth_user (id, phone, password_hash, nickname, status, failed_login_count, scope_version, created_at, updated_at) VALUES (UUID_TO_BIN(?), ?, ?, 'Administrator', 'ACTIVE', 0, 0, ?, ?)", id.toString(), normalized, encoder.encode(password), now, now);
    } else {
      jdbc.update("UPDATE auth_user SET password_hash=?, status='ACTIVE', failed_login_count=0, locked_until=NULL, updated_at=? WHERE id=UUID_TO_BIN(?)", encoder.encode(password), Instant.now(), id.toString());
    }
    jdbc.update("INSERT IGNORE INTO auth_user_role (user_id, role_id) SELECT UUID_TO_BIN(?), id FROM auth_role WHERE code='SUPER_ADMIN'", id.toString());
    jdbc.update("INSERT IGNORE INTO auth_role_permission (role_id, permission_id) SELECT r.id, p.id FROM auth_role r CROSS JOIN auth_permission p WHERE r.code='SUPER_ADMIN'");
  }
  private static String env(String name) { String value = System.getenv(name); return value == null || value.isBlank() ? null : value; }
}

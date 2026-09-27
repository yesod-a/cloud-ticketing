package com.cloudticket.auth;

import com.cloudticket.auth.persistence.entity.AuthUserEntity;
import com.cloudticket.auth.persistence.mapper.AuthRoleMapper;
import com.cloudticket.auth.persistence.mapper.AuthUserMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the optional local administrator only when both admin environment values are provided. */
@Component
public class AdminAccountInitializer implements CommandLineRunner {

  private static final String SUPER_ADMIN = "SUPER_ADMIN";

  private final AuthUserMapper users;
  private final AuthRoleMapper roles;
  private final PasswordEncoder encoder;

  public AdminAccountInitializer(AuthUserMapper users, AuthRoleMapper roles, PasswordEncoder encoder) {
    this.users = users;
    this.roles = roles;
    this.encoder = encoder;
  }

  @Override
  public void run(String... args) {
    String phone = env("AUTH_ADMIN_PHONE");
    String password = env("AUTH_ADMIN_PASSWORD");
    if (phone == null || password == null) return;
    String normalized = phone.replaceAll("[^0-9+]", "");
    if (normalized.isBlank() || password.length() < 8) {
      throw new IllegalStateException("AUTH_ADMIN_PHONE and a valid AUTH_ADMIN_PASSWORD are required");
    }
    List<UUID> existing = users.findIdsByPhone(normalized);
    UUID adminId;
    if (existing.isEmpty()) {
      adminId = UUID.randomUUID();
      AuthUserEntity admin = new AuthUserEntity();
      admin.setId(adminId);
      admin.setPhone(normalized);
      admin.setPasswordHash(encoder.encode(password));
      admin.setNickname("Administrator");
      admin.setStatus("ACTIVE");
      admin.setFailedLoginCount(0);
      admin.setScopeVersion(0L);
      users.insert(admin);
    } else {
      adminId = existing.get(0);
      users.reactivate(adminId, encoder.encode(password));
    }
    users.grantRole(adminId, SUPER_ADMIN);
    roles.grantAllPermissionsToSuperAdmin();
  }

  private static String env(String name) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? null : value;
  }
}

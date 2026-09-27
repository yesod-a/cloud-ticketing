package com.cloudticket.auth.api;

import com.cloudticket.auth.persistence.entity.AuthUserEntity;
import com.cloudticket.auth.persistence.mapper.AuthRevokedAccessTokenMapper;
import com.cloudticket.auth.persistence.mapper.AuthUserMapper;
import com.cloudticket.common.security.RequireInternalToken;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Token status check the gateway calls on every request it authenticates. */
@RestController
@RequireInternalToken
@RequestMapping("/api/internal/auth/token")
public class AuthTokenStatusController {

  private final AuthUserMapper users;
  private final AuthRevokedAccessTokenMapper revokedTokens;

  public AuthTokenStatusController(AuthUserMapper users, AuthRevokedAccessTokenMapper revokedTokens) {
    this.users = users;
    this.revokedTokens = revokedTokens;
  }

  @GetMapping("/status")
  public Map<String, Object> status(@RequestParam String userId, @RequestParam String scopeVersion,
                                    @RequestParam String jti) {
    UUID id = parseUuid(userId);
    if (id == null) return Map.of("active", false);
    AuthUserEntity user = users.selectById(id);
    if (user == null || revokedTokens.countActiveRevocations(jti) > 0) return Map.of("active", false);
    long version = user.getScopeVersion() == null ? 0 : user.getScopeVersion();
    return Map.of("active", "ACTIVE".equals(user.getStatus())
        && String.valueOf(version).equals(scopeVersion));
  }

  private static UUID parseUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (RuntimeException notAUuid) {
      return null;
    }
  }
}

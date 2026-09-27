package com.cloudticket.auth.security;

import com.cloudticket.auth.persistence.entity.AuthRefreshTokenEntity;
import com.cloudticket.auth.persistence.entity.AuthUserEntity;
import com.cloudticket.auth.persistence.mapper.AuthRefreshTokenMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Issues the short-lived access token and rotates the refresh token.
 *
 * <p>Refresh tokens are stored hashed and belong to a family; replaying a revoked token revokes the
 * whole family, so a stolen token cannot be used to keep a session alive.
 */
public class TokenService {

  private final AuthRefreshTokenMapper refreshTokens;
  private final byte[] signingKey;
  private final Duration refreshTtl;

  public TokenService(AuthRefreshTokenMapper refreshTokens) {
    this(refreshTokens, System.getenv().getOrDefault("AUTH_JWT_SIGNING_KEY", "dev-only-change-me"),
        Duration.ofDays(30));
  }

  public TokenService(AuthRefreshTokenMapper refreshTokens, String signingKey, Duration refreshTtl) {
    this.refreshTokens = refreshTokens;
    this.signingKey = signingKey.getBytes(StandardCharsets.UTF_8);
    this.refreshTtl = refreshTtl;
  }

  public Issued issue(AuthUserEntity user) {
    return issue(user, List.of(), List.of());
  }

  public Issued issue(AuthUserEntity user, List<String> roles, List<String> permissions) {
    return issue(user, roles, permissions, List.of());
  }

  public Issued issue(AuthUserEntity user, List<String> roles, List<String> permissions, List<String> scopes) {
    UUID id = UUID.randomUUID();
    UUID family = UUID.randomUUID();
    String raw = randomToken();
    store(id, user.getId(), digest(raw), family, Instant.now().plus(refreshTtl));
    return new Issued(jwt(user, roles, permissions, scopes), raw, id, family);
  }

  public Issued rotate(String raw, AuthUserEntity user) {
    return rotate(raw, user, List.of(), List.of());
  }

  public Issued rotate(String raw, AuthUserEntity user, List<String> roles, List<String> permissions) {
    return rotate(raw, user, roles, permissions, List.of());
  }

  public Issued rotate(String raw, AuthUserEntity user, List<String> roles, List<String> permissions,
                       List<String> scopes) {
    String hash = digest(raw);
    AuthRefreshTokenEntity active = refreshTokens.findActiveByHash(hash);
    if (active == null) {
      // A revoked token being presented again means the family is compromised.
      AuthRefreshTokenEntity known = refreshTokens.findByHash(hash);
      if (known != null) refreshTokens.revokeFamily(known.getFamilyId(), Instant.now());
      throw new SecurityException("Invalid refresh session");
    }
    if (!active.getUserId().equals(user.getId())) throw new SecurityException("Invalid refresh session");

    UUID nextId = UUID.randomUUID();
    String next = randomToken();
    store(nextId, user.getId(), digest(next), active.getFamilyId(), Instant.now().plus(refreshTtl));
    refreshTokens.revoke(active.getId(), Instant.now());
    return new Issued(jwt(user, roles, permissions, scopes), next, nextId, active.getFamilyId());
  }

  public AuthRefreshTokenEntity find(String raw) {
    AuthRefreshTokenEntity token = refreshTokens.findActiveByHash(digest(raw));
    if (token == null) throw new SecurityException("Invalid refresh session");
    return token;
  }

  /** Returns a token record even when revoked, so replay detection can revoke the whole family. */
  public AuthRefreshTokenEntity findAny(String raw) {
    AuthRefreshTokenEntity token = refreshTokens.findByHash(digest(raw));
    if (token == null) throw new SecurityException("Invalid refresh session");
    return token;
  }

  public void revoke(String raw) {
    AuthRefreshTokenEntity token = refreshTokens.findActiveByHash(digest(raw));
    if (token != null) refreshTokens.revoke(token.getId(), Instant.now());
  }

  public static Optional<AccessClaims> parseAccessToken(String raw) {
    try {
      String[] parts = raw.split("\\.");
      if (parts.length != 3) return Optional.empty();
      String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
      Matcher jti = Pattern.compile("\"jti\":\"([^\"]+)\"").matcher(payload);
      Matcher exp = Pattern.compile("\"exp\":(\\d+)").matcher(payload);
      if (!jti.find() || !exp.find()) return Optional.empty();
      return Optional.of(new AccessClaims(jti.group(1), Instant.ofEpochSecond(Long.parseLong(exp.group(1)))));
    } catch (Exception malformed) {
      return Optional.empty();
    }
  }

  public static String digest(String raw) {
    try {
      return hex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }

  private void store(UUID id, UUID userId, String tokenHash, UUID familyId, Instant expiresAt) {
    AuthRefreshTokenEntity token = new AuthRefreshTokenEntity();
    token.setId(id);
    token.setUserId(userId);
    token.setTokenHash(tokenHash);
    token.setFamilyId(familyId);
    token.setExpiresAt(expiresAt);
    refreshTokens.insert(token);
  }

  private String randomToken() {
    return UUID.randomUUID() + "." + UUID.randomUUID();
  }

  private String jwt(AuthUserEntity user, List<String> roles, List<String> permissions, List<String> scopes) {
    String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
    long now = Instant.now().getEpochSecond();
    long scopeVersion = user.getScopeVersion() == null ? 0 : user.getScopeVersion();
    String payload = b64("{\"sub\":\"" + user.getId() + "\",\"jti\":\"" + UUID.randomUUID()
        + "\",\"roles\":" + jsonArray(roles) + ",\"permissions\":" + jsonArray(permissions)
        + ",\"scopes\":" + jsonArray(scopes) + ",\"scopeVersion\":" + scopeVersion
        + ",\"iat\":" + now + ",\"exp\":" + (now + 900) + "}");
    String input = header + "." + payload;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
      return input + "." + Base64.getUrlEncoder().withoutPadding()
          .encodeToString(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }

  private static String jsonArray(List<String> values) {
    return values == null || values.isEmpty() ? "[]" : "[\"" + String.join("\",\"", values) + "\"]";
  }

  private static String b64(String value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String hex(byte[] bytes) {
    StringBuilder hex = new StringBuilder();
    for (byte value : bytes) hex.append(String.format("%02x", value));
    return hex.toString();
  }

  public record Issued(String accessToken, String refreshToken, UUID tokenId, UUID familyId) {}

  public record AccessClaims(String jti, Instant expiresAt) {}
}

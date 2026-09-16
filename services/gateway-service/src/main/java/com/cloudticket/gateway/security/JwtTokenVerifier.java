package com.cloudticket.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class JwtTokenVerifier {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final byte[] secret;
  public JwtTokenVerifier(String secret) {
    if (secret == null || secret.length() < 16) throw new IllegalArgumentException("JWT signing key must be at least 16 characters");
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
  }
  public Claims verify(String token) {
    try {
      String[] parts = token.split("\\."); if (parts.length != 3) throw new SecurityException("Malformed token");
      Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      byte[] expected = mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
      if (!MessageDigest.isEqual(expected, Base64.getUrlDecoder().decode(parts[2]))) throw new SecurityException("Invalid signature");
      JsonNode payload = JSON.readTree(Base64.getUrlDecoder().decode(parts[1]));
      if (payload.path("sub").asText().isBlank() || payload.path("jti").asText().isBlank() || payload.path("iat").asLong(0) <= 0 || payload.path("exp").asLong(0) <= Instant.now().getEpochSecond()) throw new SecurityException("Expired or incomplete token");
      return new Claims(payload.path("sub").asText(), payload.path("jti").asText(), csv(payload.path("roles")), csv(payload.path("permissions")), csv(payload.path("scopes")), payload.path("scopeVersion").asText("0"));
    } catch (SecurityException e) { throw e; } catch (Exception e) { throw new SecurityException("Invalid token", e); }
  }
  private static String csv(JsonNode node) { StringBuilder value = new StringBuilder(); for (JsonNode item : node) { if (!value.isEmpty()) value.append(','); value.append(item.asText()); } return value.toString(); }
  public record Claims(String subject, String jti, String roles, String permissions, String scopes, String scopeVersion) {}
}

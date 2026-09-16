package com.cloudticket.auth.security;

import com.cloudticket.auth.domain.RefreshTokenEntity;
import com.cloudticket.auth.domain.UserEntity;
import com.cloudticket.auth.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class TokenService {
    private final RefreshTokenRepository refreshTokens;
    private final byte[] signingKey;
    private final Duration refreshTtl;
    public TokenService(RefreshTokenRepository refreshTokens) { this(refreshTokens, System.getenv().getOrDefault("AUTH_JWT_SIGNING_KEY", "dev-only-change-me"), Duration.ofDays(30)); }
    public TokenService(RefreshTokenRepository refreshTokens, String signingKey, Duration refreshTtl) {
        this.refreshTokens = refreshTokens; this.signingKey = signingKey.getBytes(StandardCharsets.UTF_8); this.refreshTtl = refreshTtl;
    }
    public Issued issue(UserEntity user) {
        return issue(user, List.of(), List.of());
    }
    public Issued issue(UserEntity user, List<String> roles, List<String> permissions) {
        return issue(user, roles, permissions, List.of());
    }
    public Issued issue(UserEntity user, List<String> roles, List<String> permissions, List<String> scopes) {
        UUID id = UUID.randomUUID(), family = UUID.randomUUID(); String raw = randomToken();
        Instant now = Instant.now();
        refreshTokens.insert(id.toString(), user.id().toString(), digest(raw), family.toString(), now.plus(refreshTtl), null, null, now);
        return new Issued(jwt(user, roles, permissions, scopes), raw, id, family);
    }
    public Issued rotate(String raw, UserEntity user) {
        return rotate(raw, user, List.of(), List.of());
    }
    public Issued rotate(String raw, UserEntity user, List<String> roles, List<String> permissions) {
        return rotate(raw, user, roles, permissions, List.of());
    }
    public Issued rotate(String raw, UserEntity user, List<String> roles, List<String> permissions, List<String> scopes) {
        String hash = digest(raw);
        RefreshTokenEntity old = refreshTokens.findActiveByHash(hash).orElseGet(() -> {
            refreshTokens.findByHash(hash).ifPresent(t -> refreshTokens.revokeFamily(t.familyId(), Instant.now()));
            throw new SecurityException("Invalid refresh session");
        });
        if (!old.userId().equals(user.id())) throw new SecurityException("Invalid refresh session");
        UUID id = UUID.randomUUID(); String next = randomToken();
        Instant now = Instant.now();
        refreshTokens.insert(id.toString(), user.id().toString(), digest(next), old.familyId().toString(), now.plus(refreshTtl), null, null, now);
        refreshTokens.revoke(old.id(), Instant.now());
        return new Issued(jwt(user, roles, permissions, scopes), next, id, old.familyId());
    }
    public RefreshTokenEntity find(String raw) { return refreshTokens.findActiveByHash(digest(raw)).orElseThrow(() -> new SecurityException("Invalid refresh session")); }
    /** Returns a token record even when revoked, so replay detection can revoke the whole family. */
    public RefreshTokenEntity findAny(String raw) { return refreshTokens.findByHash(digest(raw)).orElseThrow(() -> new SecurityException("Invalid refresh session")); }
    public void revoke(String raw) { refreshTokens.findActiveByHash(digest(raw)).ifPresent(t -> refreshTokens.revoke(t.id(), Instant.now())); }
    public static Optional<AccessClaims> parseAccessToken(String raw) { try { String[] parts=raw.split("\\."); if(parts.length!=3) return Optional.empty(); String payload=new String(Base64.getUrlDecoder().decode(parts[1]),StandardCharsets.UTF_8); Matcher jti=Pattern.compile("\\\"jti\\\":\\\"([^\\\"]+)\\\"").matcher(payload); Matcher exp=Pattern.compile("\\\"exp\\\":(\\d+)").matcher(payload); if(!jti.find()||!exp.find()) return Optional.empty(); return Optional.of(new AccessClaims(jti.group(1),Instant.ofEpochSecond(Long.parseLong(exp.group(1))))); } catch(Exception e) { return Optional.empty(); } }
    public static String digest(String raw) { try { return hex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String randomToken() { return UUID.randomUUID()+"."+UUID.randomUUID(); }
    private String jwt(UserEntity u, List<String> roles, List<String> permissions) {
        return jwt(u, roles, permissions, List.of());
    }
    private String jwt(UserEntity u, List<String> roles, List<String> permissions, List<String> scopes) {
        String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        long now = Instant.now().getEpochSecond();
        String payload = b64("{\"sub\":\""+u.id()+"\",\"jti\":\""+UUID.randomUUID()+"\",\"roles\":"+jsonArray(roles)+",\"permissions\":"+jsonArray(permissions)+",\"scopes\":"+jsonArray(scopes)+",\"scopeVersion\":"+u.scopeVersion()+",\"iat\":"+now+",\"exp\":"+(now+900)+"}");
        String input = header+"."+payload; try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(signingKey,"HmacSHA256")); return input+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(input.getBytes(StandardCharsets.UTF_8))); } catch(Exception e){throw new IllegalStateException(e);}
    }
    private static String jsonArray(List<String> values) { return values == null || values.isEmpty() ? "[]" : "[\""+String.join("\",\"", values)+"\"]"; }
    private static String b64(String s) { return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
    private static String hex(byte[] b) { StringBuilder s=new StringBuilder(); for(byte x:b)s.append(String.format("%02x",x)); return s.toString(); }
    public record Issued(String accessToken, String refreshToken, UUID tokenId, UUID familyId) {}
    public record AccessClaims(String jti, Instant expiresAt) {}
}

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
        UUID id = UUID.randomUUID(), family = UUID.randomUUID(); String raw = randomToken();
        refreshTokens.save(new RefreshTokenEntity(id, user.id(), digest(raw), family, Instant.now().plus(refreshTtl), null, null, Instant.now()));
        return new Issued(jwt(user), raw, id, family);
    }
    public Issued rotate(String raw, UserEntity user) {
        String hash = digest(raw);
        RefreshTokenEntity old = refreshTokens.findActiveByHash(hash).orElseGet(() -> {
            refreshTokens.findByHash(hash).ifPresent(t -> refreshTokens.revokeFamily(t.familyId(), Instant.now()));
            throw new SecurityException("Invalid refresh session");
        });
        if (!old.userId().equals(user.id())) throw new SecurityException("Invalid refresh session");
        UUID id = UUID.randomUUID(); String next = randomToken();
        refreshTokens.save(new RefreshTokenEntity(id, user.id(), digest(next), old.familyId(), Instant.now().plus(refreshTtl), null, null, Instant.now()));
        refreshTokens.revoke(old.id(), Instant.now());
        return new Issued(jwt(user), next, id, old.familyId());
    }
    public RefreshTokenEntity find(String raw) { return refreshTokens.findActiveByHash(digest(raw)).orElseThrow(() -> new SecurityException("Invalid refresh session")); }
    public void revoke(String raw) { refreshTokens.findActiveByHash(digest(raw)).ifPresent(t -> refreshTokens.revoke(t.id(), Instant.now())); }
    public static String digest(String raw) { try { return hex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String randomToken() { return UUID.randomUUID()+"."+UUID.randomUUID(); }
    private String jwt(UserEntity u) {
        String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        long now = Instant.now().getEpochSecond();
        String payload = b64("{\"sub\":\""+u.id()+"\",\"jti\":\""+UUID.randomUUID()+"\",\"roles\":[],\"permissions\":[],\"scopeVersion\":"+u.scopeVersion()+",\"iat\":"+now+",\"exp\":"+(now+900)+"}");
        String input = header+"."+payload; try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(signingKey,"HmacSHA256")); return input+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(input.getBytes(StandardCharsets.UTF_8))); } catch(Exception e){throw new IllegalStateException(e);}
    }
    private static String b64(String s) { return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
    private static String hex(byte[] b) { StringBuilder s=new StringBuilder(); for(byte x:b)s.append(String.format("%02x",x)); return s.toString(); }
    public record Issued(String accessToken, String refreshToken, UUID tokenId, UUID familyId) {}
}

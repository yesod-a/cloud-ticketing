package com.cloudticket.auth.service;

import com.cloudticket.auth.persistence.entity.AuthUserEntity;
import com.cloudticket.auth.persistence.mapper.AuthRevokedAccessTokenMapper;
import com.cloudticket.auth.persistence.mapper.AuthUserMapper;
import com.cloudticket.auth.profile.AvatarStorage;
import com.cloudticket.auth.security.PasswordPolicy;
import com.cloudticket.auth.security.TokenService;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Registration, login, refresh and password recovery for a user account. */
@Service
public class AuthService {

  private static final int MAX_FAILURES = 5;
  private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
  private static final String ACTIVE = "ACTIVE";

  private final AuthUserMapper users;
  private final PasswordEncoder encoder;
  private final TokenService tokens;
  private final AuthRevokedAccessTokenMapper revokedTokens;
  private final AvatarStorage avatars;
  private final ConcurrentHashMap<String, UUID> resetTokens = new ConcurrentHashMap<>();

  public AuthService(AuthUserMapper users, PasswordEncoder encoder, TokenService tokens) {
    this(users, encoder, tokens, null, null);
  }

  public AuthService(AuthUserMapper users, PasswordEncoder encoder, TokenService tokens,
                     AuthRevokedAccessTokenMapper revokedTokens) {
    this(users, encoder, tokens, revokedTokens, null);
  }

  @Autowired
  public AuthService(AuthUserMapper users, PasswordEncoder encoder, TokenService tokens,
                     AuthRevokedAccessTokenMapper revokedTokens, AvatarStorage avatars) {
    this.users = users;
    this.encoder = encoder;
    this.tokens = tokens;
    this.revokedTokens = revokedTokens;
    this.avatars = avatars;
  }

  public AuthUserEntity register(String phone, String email, String password, String nickname) {
    String cleanPhone = normalizePhone(phone);
    String cleanEmail = normalizeEmail(email);
    PasswordPolicy.requireValid(password);
    if (cleanPhone == null && cleanEmail == null) throw new IllegalArgumentException("phone or email required");
    if ((cleanPhone != null && users.countByPhone(cleanPhone) > 0)
        || (cleanEmail != null && users.countByEmail(cleanEmail) > 0)) {
      throw new DuplicateCredentialException();
    }
    AuthUserEntity user = new AuthUserEntity();
    user.setId(UUID.randomUUID());
    user.setPhone(cleanPhone);
    user.setEmail(cleanEmail);
    user.setPasswordHash(encoder.encode(password));
    user.setNickname(nickname);
    user.setStatus(ACTIVE);
    user.setFailedLoginCount(0);
    user.setScopeVersion(0L);
    users.insert(user);
    return users.selectById(user.getId());
  }

  public TokenService.Issued login(String identifier, String password) {
    String normalized = identifier != null && identifier.contains("@")
        ? normalizeEmail(identifier)
        : normalizePhone(identifier);
    AuthUserEntity user = Optional.ofNullable(normalized == null ? null : users.findByIdentifier(normalized))
        .orElseThrow(InvalidCredentialsException::new);
    if (!ACTIVE.equals(user.getStatus())
        || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now()))) {
      throw new InvalidCredentialsException();
    }
    if (!encoder.matches(password, user.getPasswordHash())) {
      int failures = failures(user) + 1;
      Instant lock = failures >= MAX_FAILURES ? Instant.now().plus(LOCK_DURATION) : user.getLockedUntil();
      users.updateLoginState(user.getId(), failures, lock);
      throw new InvalidCredentialsException();
    }
    if (failures(user) != 0 || user.getLockedUntil() != null) {
      users.updateLoginState(user.getId(), 0, null);
    }
    return tokens.issue(user, users.findRoleCodes(user.getId()), users.findPermissionCodes(user.getId()),
        users.findScopes(user.getId()));
  }

  public TokenService.Issued refresh(String refreshToken) {
    try {
      var stored = tokens.findAny(refreshToken);
      AuthUserEntity user = Optional.ofNullable(users.selectById(stored.getUserId()))
          .orElseThrow(InvalidCredentialsException::new);
      return tokens.rotate(refreshToken, user, users.findRoleCodes(user.getId()),
          users.findPermissionCodes(user.getId()), users.findScopes(user.getId()));
    } catch (SecurityException failure) {
      throw new InvalidCredentialsException();
    }
  }

  public void logout(String refreshToken) {
    logout(refreshToken, null);
  }

  public void logout(String refreshToken, String accessToken) {
    tokens.revoke(refreshToken);
    if (revokedTokens == null || accessToken == null || accessToken.isBlank()) return;
    TokenService.parseAccessToken(accessToken)
        .ifPresent(claims -> revokedTokens.revoke(claims.jti(), claims.expiresAt()));
  }

  public String forgotPassword(String identifier) {
    String normalized = identifier != null && identifier.contains("@")
        ? normalizeEmail(identifier)
        : normalizePhone(identifier);
    if (normalized != null) {
      Optional.ofNullable(users.findByIdentifier(normalized))
          .ifPresent(user -> resetTokens.put(UUID.randomUUID().toString(), user.getId()));
    }
    return "If the account exists, reset instructions were sent";
  }

  public AuthUserEntity resetPassword(String token, String password) {
    PasswordPolicy.requireValid(password);
    UUID id = resetTokens.remove(token);
    if (id == null) throw new InvalidCredentialsException();
    AuthUserEntity user = Optional.ofNullable(users.selectById(id))
        .orElseThrow(InvalidCredentialsException::new);
    users.resetPassword(user.getId(), encoder.encode(password));
    tokens.revokeAllForUser(user.getId());
    return users.selectById(user.getId());
  }

  public AuthUserEntity me(UUID userId) {
    return Optional.ofNullable(users.selectById(userId)).orElseThrow(InvalidCredentialsException::new);
  }

  public List<AuthUserEntity> publicProfiles(List<UUID> ids) {
    if (ids == null || ids.isEmpty()) return List.of();
    return users.selectPublicProfiles(ids.stream().distinct().limit(100).toList());
  }

  public AuthUserEntity updateNickname(UUID userId, String nickname) {
    AuthUserEntity user = me(userId);
    String clean = nickname == null ? null : nickname.trim();
    if (clean != null && clean.length() > 120) {
      throw new IllegalArgumentException("Nickname must be at most 120 characters");
    }
    if (clean != null && clean.isEmpty()) clean = null;
    users.updateNickname(userId, clean);
    user.setNickname(clean);
    return profile(userId, user);
  }

  public void changePassword(UUID userId, String currentPassword, String newPassword) {
    AuthUserEntity user = me(userId);
    if (!encoder.matches(currentPassword, user.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }
    PasswordPolicy.requireValid(newPassword);
    users.resetPassword(userId, encoder.encode(newPassword));
    tokens.revokeAllForUser(userId);
  }

  public AuthUserEntity updateAvatar(UUID userId, MultipartFile file) {
    AuthUserEntity user = me(userId);
    if (avatars == null) throw new IllegalStateException("Avatar storage is unavailable");
    AvatarStorage.extension(file);
    String previous = user.getAvatarFilename();
    String filename = avatars.save(userId, file);
    if (users.updateAvatarFilename(userId, filename) != 1) {
      avatars.delete(filename);
      throw new IllegalStateException("Could not update avatar");
    }
    if (previous != null && !previous.equals(filename)) avatars.delete(previous);
    user.setAvatarFilename(filename);
    return profile(userId, user);
  }

  public Optional<AvatarStorage.StoredAvatar> openAvatar(UUID userId) {
    AuthUserEntity user = users.selectById(userId);
    if (user == null) return Optional.empty();
    if (avatars == null || user.getAvatarFilename() == null) return Optional.empty();
    return avatars.open(user.getAvatarFilename());
  }

  private AuthUserEntity profile(UUID userId, AuthUserEntity fallback) {
    return Optional.ofNullable(users.selectProfile(userId)).orElse(fallback);
  }

  public static String normalizeEmail(String email) {
    return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
  }

  public static String normalizePhone(String phone) {
    if (phone == null) return null;
    String normalized = phone.replaceAll("[^0-9+]", "");
    return normalized.isBlank() ? null : normalized;
  }

  private static int failures(AuthUserEntity user) {
    return user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount();
  }

  public static class DuplicateCredentialException extends RuntimeException {}

  public static class InvalidCredentialsException extends RuntimeException {}

  public static class AvatarNotFoundException extends RuntimeException {}
}

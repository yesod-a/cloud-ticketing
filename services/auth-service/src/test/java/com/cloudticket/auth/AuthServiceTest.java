package com.cloudticket.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.auth.persistence.entity.AuthRefreshTokenEntity;
import com.cloudticket.auth.persistence.entity.AuthUserEntity;
import com.cloudticket.auth.persistence.mapper.AuthRefreshTokenMapper;
import com.cloudticket.auth.persistence.mapper.AuthUserMapper;
import com.cloudticket.auth.security.TokenService;
import com.cloudticket.auth.service.AuthService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthServiceTest {

  private final AuthUserMapper users = mock(AuthUserMapper.class);
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  @Test
  void duplicateContactsAreRejected() {
    when(users.countByPhone("13800138000")).thenReturn(1);
    AuthService service = new AuthService(users, encoder, mock(TokenService.class));

    assertThrows(AuthService.DuplicateCredentialException.class,
        () -> service.register("13800138000", null, "StrongPass1", "n"));
  }

  @Test
  void inactiveUsersCannotLogin() {
    when(users.findByIdentifier("13800138000"))
        .thenReturn(user(UUID.randomUUID(), "13800138000", "DISABLED", 0, null));
    AuthService service = new AuthService(users, encoder, mock(TokenService.class));

    assertThrows(AuthService.InvalidCredentialsException.class,
        () -> service.login("13800138000", "StrongPass1"));
  }

  @Test
  void registrationUsesTheGeneratedIdAndNormalisedContacts() {
    when(users.selectById(any())).thenAnswer(invocation -> user(invocation.getArgument(0), "13800138001",
        "ACTIVE", 0, null));
    AuthService service = new AuthService(users, encoder, mock(TokenService.class));

    var registered = service.register(" 138-0013-8001 ", null, "StrongPass1", "n");

    ArgumentCaptor<AuthUserEntity> inserted = ArgumentCaptor.forClass(AuthUserEntity.class);
    verify(users).insert(inserted.capture());
    assertNotNull(inserted.getValue().getId());
    assertEquals("13800138001", inserted.getValue().getPhone());
    assertEquals("ACTIVE", inserted.getValue().getStatus());
    assertTrue(encoder.matches("StrongPass1", inserted.getValue().getPasswordHash()));
    assertEquals("13800138001", registered.getPhone());
  }

  @Test
  void registrationRejectsAPasswordThatBreachesThePolicy() {
    AuthService service = new AuthService(users, encoder, mock(TokenService.class));

    assertThrows(IllegalArgumentException.class,
        () -> service.register("13800138009", null, "weak", "n"));
    verify(users, never()).insert(any(AuthUserEntity.class));
  }

  @Test
  void wrongPasswordCountsAFailure() {
    UUID id = UUID.randomUUID();
    when(users.findByIdentifier("13800138000"))
        .thenReturn(user(id, "13800138000", "ACTIVE", 2, null));
    AuthService service = new AuthService(users, encoder, mock(TokenService.class));

    assertThrows(AuthService.InvalidCredentialsException.class,
        () -> service.login("13800138000", "WrongPass1"));

    verify(users).updateLoginState(id, 3, null);
  }

  @Test
  void fifthFailureLocksTheAccount() {
    UUID id = UUID.randomUUID();
    when(users.findByIdentifier("13800138000"))
        .thenReturn(user(id, "13800138000", "ACTIVE", 4, null));
    AuthService service = new AuthService(users, encoder, mock(TokenService.class));

    assertThrows(AuthService.InvalidCredentialsException.class,
        () -> service.login("13800138000", "WrongPass1"));

    verify(users).updateLoginState(eq(id), eq(5), any(Instant.class));
  }

  @Test
  void successfulLoginClearsTheFailureCounter() {
    UUID id = UUID.randomUUID();
    when(users.findByIdentifier("13800138000"))
        .thenReturn(user(id, "13800138000", "ACTIVE", 3, null));
    TokenService tokens = mock(TokenService.class);
    when(tokens.issue(any(), any(), any(), any()))
        .thenReturn(new TokenService.Issued("access", "refresh", UUID.randomUUID(), UUID.randomUUID()));
    AuthService service = new AuthService(users, encoder, tokens);

    service.login("13800138000", "StrongPass1");

    verify(users).updateLoginState(id, 0, null);
    verify(tokens).issue(any(), any(), any(), any());
  }

  @Test
  void lockedAccountsAreRejectedBeforeThePasswordIsChecked() {
    when(users.findByIdentifier("13800138000"))
        .thenReturn(user(UUID.randomUUID(), "13800138000", "ACTIVE", 5, Instant.now().plusSeconds(600)));
    AuthService service = new AuthService(users, encoder, mock(TokenService.class));

    assertThrows(AuthService.InvalidCredentialsException.class,
        () -> service.login("13800138000", "StrongPass1"));
    verify(users, never()).updateLoginState(any(), anyInt(), any());
  }

  @Test
  void refreshReplayUsesTheRevokedTokenToRevokeItsFamily() {
    TokenService tokenService = mock(TokenService.class);
    UUID userId = UUID.randomUUID();
    AuthRefreshTokenEntity revoked = new AuthRefreshTokenEntity();
    revoked.setId(UUID.randomUUID());
    revoked.setUserId(userId);
    revoked.setFamilyId(UUID.randomUUID());
    when(tokenService.findAny("old-refresh")).thenReturn(revoked);
    when(users.selectById(userId)).thenReturn(user(userId, "13800138004", "ACTIVE", 0, null));
    TokenService.Issued issued = new TokenService.Issued("access", "next", UUID.randomUUID(), UUID.randomUUID());
    when(tokenService.rotate(eq("old-refresh"), any(), any(), any(), any())).thenReturn(issued);
    AuthService service = new AuthService(users, encoder, tokenService);

    assertSame(issued, service.refresh("old-refresh"));

    verify(tokenService).rotate(eq("old-refresh"), any(), any(), any(), any());
  }

  @Test
  void issuedTokenContainsRolesPermissionsAndScopes() {
    AuthRefreshTokenMapper refreshTokens = mock(AuthRefreshTokenMapper.class);
    TokenService tokenService = new TokenService(refreshTokens, "test-key", Duration.ofDays(1));
    AuthUserEntity user = user(UUID.randomUUID(), "13800138002", "ACTIVE", 0, null);

    String token = tokenService.issue(user, List.of("SUPER_ADMIN"), List.of("user:manage"),
        List.of("ACTIVITY:activity-1")).accessToken();
    String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);

    assertTrue(payload.contains("SUPER_ADMIN"));
    assertTrue(payload.contains("user:manage"));
    assertTrue(payload.contains("ACTIVITY:activity-1"));
    verify(refreshTokens).insert(any(AuthRefreshTokenEntity.class));
  }

  @Test
  void refreshRotationStoresTheNextTokenAndRevokesTheOldOne() {
    AuthRefreshTokenMapper refreshTokens = mock(AuthRefreshTokenMapper.class);
    TokenService tokenService = new TokenService(refreshTokens, "test-key", Duration.ofDays(1));
    AuthUserEntity user = user(UUID.randomUUID(), "13800138005", "ACTIVE", 0, null);
    AuthRefreshTokenEntity active = new AuthRefreshTokenEntity();
    active.setId(UUID.randomUUID());
    active.setUserId(user.getId());
    active.setFamilyId(UUID.randomUUID());
    when(refreshTokens.findActiveByHash(TokenService.digest("raw"))).thenReturn(active);

    TokenService.Issued issued = tokenService.rotate("raw", user);

    assertEquals(active.getFamilyId(), issued.familyId());
    verify(refreshTokens).insert(any(AuthRefreshTokenEntity.class));
    verify(refreshTokens).revoke(eq(active.getId()), any(Instant.class));
  }

  @Test
  void replayingARevokedRefreshTokenRevokesTheWholeFamily() {
    AuthRefreshTokenMapper refreshTokens = mock(AuthRefreshTokenMapper.class);
    TokenService tokenService = new TokenService(refreshTokens, "test-key", Duration.ofDays(1));
    UUID familyId = UUID.randomUUID();
    AuthRefreshTokenEntity revoked = new AuthRefreshTokenEntity();
    revoked.setFamilyId(familyId);
    when(refreshTokens.findByHash(TokenService.digest("replayed"))).thenReturn(revoked);

    assertThrows(SecurityException.class,
        () -> tokenService.rotate("replayed", user(UUID.randomUUID(), "1", "ACTIVE", 0, null)));
    verify(refreshTokens).revokeFamily(eq(familyId), any(Instant.class));
  }

  @Test
  void unknownRefreshTokenIsRejected() {
    AuthRefreshTokenMapper refreshTokens = mock(AuthRefreshTokenMapper.class);
    TokenService tokenService = new TokenService(refreshTokens, "test-key", Duration.ofDays(1));

    assertThrows(SecurityException.class, () -> tokenService.findAny("missing"));
    verify(refreshTokens, never()).revokeFamily(any(), any());
  }

  @Test
  void accessTokenParsingKeepsTheRevocationKey() {
    AuthRefreshTokenMapper refreshTokens = mock(AuthRefreshTokenMapper.class);
    TokenService tokenService = new TokenService(refreshTokens, "test-key", Duration.ofDays(1));

    String token = tokenService.issue(user(UUID.randomUUID(), "1", "ACTIVE", 0, null)).accessToken();
    var claims = TokenService.parseAccessToken(token).orElseThrow();

    assertNotNull(claims.jti());
    assertTrue(claims.expiresAt().isAfter(Instant.now()));
    assertTrue(TokenService.parseAccessToken("not-a-jwt").isEmpty());
  }

  @Test
  void logoutRevokesTheAccessTokenWhenTheMapperIsAvailable() {
    AuthRefreshTokenMapper refreshTokens = mock(AuthRefreshTokenMapper.class);
    var revoked = mock(com.cloudticket.auth.persistence.mapper.AuthRevokedAccessTokenMapper.class);
    TokenService tokenService = new TokenService(refreshTokens, "test-key", Duration.ofDays(1));
    String accessToken = tokenService.issue(user(UUID.randomUUID(), "1", "ACTIVE", 0, null)).accessToken();
    AuthService service = new AuthService(users, encoder, tokenService, revoked);

    service.logout("raw-refresh", accessToken);

    verify(revoked).revoke(any(), any(Instant.class));
  }

  @Test
  void meRejectsAnUnknownUser() {
    AuthService service = new AuthService(users, encoder, mock(TokenService.class));
    when(users.selectById(any())).thenReturn(null);

    assertThrows(AuthService.InvalidCredentialsException.class, () -> service.me(UUID.randomUUID()));
  }

  private static AuthUserEntity user(UUID id, String phone, String status, int failures, Instant lockedUntil) {
    AuthUserEntity user = new AuthUserEntity();
    user.setId(id);
    user.setPhone(phone);
    user.setPasswordHash(new BCryptPasswordEncoder().encode("StrongPass1"));
    user.setNickname("n");
    user.setStatus(status);
    user.setFailedLoginCount(failures);
    user.setLockedUntil(lockedUntil);
    user.setScopeVersion(0L);
    user.setCreatedAt(Instant.now());
    user.setUpdatedAt(Instant.now());
    return user;
  }
}

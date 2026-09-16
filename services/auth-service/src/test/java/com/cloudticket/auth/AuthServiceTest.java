package com.cloudticket.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cloudticket.auth.domain.UserEntity;
import com.cloudticket.auth.repository.UserRepository;
import com.cloudticket.auth.security.TokenService;
import com.cloudticket.auth.service.AuthService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthServiceTest {
  @Test void duplicateContactsAreRejected() {
    UserRepository repo = mock(UserRepository.class);
    when(repo.existsByPhone("13800138000")).thenReturn(true);
    AuthService service = new AuthService(repo, new BCryptPasswordEncoder(), mock(TokenService.class));
    assertThrows(AuthService.DuplicateCredentialException.class, () -> service.register("13800138000", null, "StrongPass1", "n"));
  }
  @Test void inactiveUsersCannotLogin() {
    UserRepository repo = mock(UserRepository.class); UUID id = UUID.randomUUID();
    when(repo.findByPhoneOrEmail(any())).thenReturn(Optional.of(new UserEntity(id,"13800138000",null,new BCryptPasswordEncoder().encode("StrongPass1"),"n","DISABLED",0,null,0,Instant.now(),Instant.now())));
    AuthService service = new AuthService(repo, new BCryptPasswordEncoder(), mock(TokenService.class));
    assertThrows(AuthService.InvalidCredentialsException.class, () -> service.login("13800138000", "StrongPass1"));
  }
  @Test void registrationUsesInsertForGeneratedIds() {
    UserRepository repo = mock(UserRepository.class);
    AuthService service = new AuthService(repo, new BCryptPasswordEncoder(), mock(TokenService.class));
    var user = service.register("13800138001", null, "StrongPass1", "n");
    assertEquals("13800138001", user.phone());
    verify(repo).insert(eq(user.id().toString()), eq("13800138001"), isNull(), anyString(), eq("n"), eq("ACTIVE"), eq(0), isNull(), eq(0L), any(), any());
  }
  @Test void issuedTokenContainsRolesAndPermissions() {
    var repository = mock(com.cloudticket.auth.repository.RefreshTokenRepository.class);
    var tokenService = new TokenService(repository, "test-key", java.time.Duration.ofDays(1));
    var user = new UserEntity(UUID.randomUUID(), "13800138002", null, "hash", "n", "ACTIVE", 0, null, 0, Instant.now(), Instant.now());
    String token = tokenService.issue(user, List.of("SUPER_ADMIN"), List.of("user:manage")).accessToken();
    String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
    assertTrue(payload.contains("SUPER_ADMIN"));
    assertTrue(payload.contains("user:manage"));
  }

  @Test void issuedTokenContainsAssignedScopes() {
    var repository = mock(com.cloudticket.auth.repository.RefreshTokenRepository.class);
    var tokenService = new TokenService(repository, "test-key", java.time.Duration.ofDays(1));
    var user = new UserEntity(UUID.randomUUID(), "13800138003", null, "hash", "n", "ACTIVE", 0, null, 2, Instant.now(), Instant.now());
    String token = tokenService.issue(user, List.of("OPERATOR"), List.of("activity:read"), List.of("ACTIVITY:activity-1")).accessToken();
    String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
    assertTrue(payload.contains("ACTIVITY:activity-1"));
  }

  @Test void refreshReplayUsesRevokedTokenToRevokeItsFamily() {
    UserRepository repo = mock(UserRepository.class);
    TokenService tokenService = mock(TokenService.class);
    UUID userId = UUID.randomUUID();
    UserEntity user = new UserEntity(userId, "13800138004", null, "hash", "n", "ACTIVE", 0, null, 0, Instant.now(), Instant.now());
    when(tokenService.findAny("old-refresh")).thenReturn(new com.cloudticket.auth.domain.RefreshTokenEntity(UUID.randomUUID(), userId, "hash", UUID.randomUUID(), Instant.now().plusSeconds(100), Instant.now(), null, Instant.now()));
    TokenService.Issued issued = new TokenService.Issued("access", "next", UUID.randomUUID(), UUID.randomUUID());
    when(repo.findById(userId)).thenReturn(Optional.of(user));
    when(repo.findRoleCodes(userId.toString())).thenReturn(List.of());
    when(repo.findPermissionCodes(userId.toString())).thenReturn(List.of());
    when(repo.findScopes(userId.toString())).thenReturn(List.of());
    when(tokenService.rotate(eq("old-refresh"), eq(user), any(), any(), any())).thenReturn(issued);
    AuthService service = new AuthService(repo, new BCryptPasswordEncoder(), tokenService);
    assertSame(issued, service.refresh("old-refresh"));
    verify(tokenService).rotate(eq("old-refresh"), eq(user), any(), any(), any());
  }
}

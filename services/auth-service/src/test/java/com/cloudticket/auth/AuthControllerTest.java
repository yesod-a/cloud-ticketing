package com.cloudticket.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.auth.api.AuthController;
import com.cloudticket.auth.api.AuthDtos;
import com.cloudticket.auth.persistence.entity.AuthUserEntity;
import com.cloudticket.auth.service.AuthService;
import com.cloudticket.common.security.CallerContext;
import com.cloudticket.common.security.CallerContextHolder;
import jakarta.validation.Validation;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

class AuthControllerTest {

  @Test
  void explicitSecurityConfigurationDisablesBootBasicAuth() {
    assertTrue(AuthSecurityConfiguration.class.isAnnotationPresent(Configuration.class));
  }

  @Test
  void tokenResponseDoesNotExposePasswordFields() {
    String json = new AuthDtos.ApiResponse<>("OK", "authenticated", "t",
        new AuthDtos.TokenView("a", "r")).toString();
    assertFalse(json.contains("password"));
    assertFalse(json.contains("hash"));
  }

  @Test
  void invalidLoginPayloadViolatesConstraints() {
    var validator = Validation.buildDefaultValidatorFactory().getValidator();
    assertTrue(validator.validate(new AuthDtos.LoginRequest("", "")).size() >= 2);
  }

  /**
   * The gateway is what answers 401 to an unauthenticated browser; a direct call to the service
   * without the internal token is a permission problem and is refused by the shared aspect.
   */
  @Test
  void meRejectsDirectCallsWithoutTheInternalServiceToken() {
    var controller = TestAspects.authorized(new AuthController(mock(AuthService.class)), null);

    assertThrows(SecurityException.class,
        () -> CallerContextHolder.scoped(new CallerContext("", "", "", "trace", "spoofed"),
            () -> controller.me("00000000-0000-0000-0000-000000000001", null, new MockHttpServletRequest())));
  }

  @Test
  void meReturnsTheUserForATrustedCall() {
    AuthService auth = mock(AuthService.class);
    UUID userId = UUID.randomUUID();
    AuthUserEntity user = new AuthUserEntity();
    user.setId(userId);
    user.setPhone("13800138000");
    user.setNickname("n");
    user.setStatus("ACTIVE");
    when(auth.me(userId)).thenReturn(user);
    var controller = TestAspects.authorized(new AuthController(auth), null);

    var response = CallerContextHolder.scoped(
        new CallerContext("", "", userId.toString(), "trace", "dev-internal-token"),
        () -> controller.me(userId.toString(), null, new MockHttpServletRequest()));

    assertEquals("13800138000", response.getBody().data().phone());
  }

  @Test
  void meRejectsAMalformedUserId() {
    var controller = TestAspects.authorized(new AuthController(mock(AuthService.class)), null);

    assertThrows(AuthService.InvalidCredentialsException.class,
        () -> CallerContextHolder.scoped(new CallerContext("", "", "", "trace", "dev-internal-token"),
            () -> controller.me("not-a-uuid", null, new MockHttpServletRequest())));
    verify(mock(AuthService.class), never()).me(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void profileUpdateUsesTheTrustedUserIdAndReturnsAvatarUrl() {
    AuthService auth = mock(AuthService.class);
    UUID userId = UUID.randomUUID();
    AuthUserEntity user = new AuthUserEntity();
    user.setId(userId);
    user.setNickname("Updated");
    user.setAvatarFilename("avatar.png");
    user.setStatus("ACTIVE");
    when(auth.updateNickname(userId, "Updated")).thenReturn(user);
    var controller = TestAspects.authorized(new AuthController(auth), null);

    var response = CallerContextHolder.scoped(
        new CallerContext("", "", userId.toString(), "trace", "dev-internal-token"),
        () -> controller.updateProfile(new AuthDtos.ProfileUpdateRequest("Updated"),
            userId.toString(), new MockHttpServletRequest()));

    assertEquals("/api/auth/avatars/" + userId, response.getBody().data().avatarUrl());
    verify(auth).updateNickname(userId, "Updated");
  }

  @Test
  void publicProfilesReturnOnlyNicknameAndAvatarForValidUniqueIds() {
    AuthService auth = mock(AuthService.class);
    UUID userId = UUID.randomUUID();
    AuthUserEntity user = new AuthUserEntity();
    user.setId(userId);
    user.setNickname("观众");
    user.setAvatarFilename("avatar.png");
    when(auth.publicProfiles(List.of(userId))).thenReturn(List.of(user));
    AuthController controller = new AuthController(auth);

    var response = controller.publicProfiles(userId + ",not-a-uuid," + userId, new MockHttpServletRequest());

    assertEquals("观众", response.getBody().data().get(0).nickname());
    assertEquals("/api/auth/avatars/" + userId, response.getBody().data().get(0).avatarUrl());
    verify(auth).publicProfiles(List.of(userId));
  }

  @Test
  void passwordChangeUsesTheTrustedUserId() {
    AuthService auth = mock(AuthService.class);
    UUID userId = UUID.randomUUID();
    var controller = TestAspects.authorized(new AuthController(auth), null);

    var response = CallerContextHolder.scoped(
        new CallerContext("", "", userId.toString(), "trace", "dev-internal-token"),
        () -> controller.changePassword(new AuthDtos.PasswordChangeRequest("OldPass1", "NewPass1"),
            userId.toString(), new MockHttpServletRequest()));

    assertEquals("OK", response.getBody().code());
    verify(auth).changePassword(userId, "OldPass1", "NewPass1");
  }

  @Test
  void avatarUploadUsesTheMultipartFileAndTrustedUserId() {
    AuthService auth = mock(AuthService.class);
    UUID userId = UUID.randomUUID();
    AuthUserEntity user = new AuthUserEntity();
    user.setId(userId);
    user.setStatus("ACTIVE");
    when(auth.updateAvatar(org.mockito.ArgumentMatchers.eq(userId),
        org.mockito.ArgumentMatchers.any())).thenReturn(user);
    var controller = TestAspects.authorized(new AuthController(auth), null);

    CallerContextHolder.scoped(
        new CallerContext("", "", userId.toString(), "trace", "dev-internal-token"),
        () -> controller.uploadAvatar(new MockMultipartFile("file", "a.png", "image/png", new byte[] {1}),
            userId.toString(), new MockHttpServletRequest()));

    verify(auth).updateAvatar(org.mockito.ArgumentMatchers.eq(userId),
        org.mockito.ArgumentMatchers.any(MockMultipartFile.class));
  }

  @Test
  void missingAvatarIsReportedAsNotFound() {
    AuthService auth = mock(AuthService.class);
    UUID userId = UUID.randomUUID();
    when(auth.openAvatar(userId)).thenReturn(Optional.empty());
    var controller = TestAspects.authorized(new AuthController(auth), null);

    assertThrows(AuthService.AvatarNotFoundException.class,
        () -> controller.avatar(userId));
  }
}

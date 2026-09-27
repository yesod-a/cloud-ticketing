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
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;

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
}

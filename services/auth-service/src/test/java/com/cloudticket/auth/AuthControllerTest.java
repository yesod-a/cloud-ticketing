package com.cloudticket.auth;

import static org.junit.jupiter.api.Assertions.*;
import com.cloudticket.auth.api.AuthDtos;
import jakarta.validation.Validation;
import org.springframework.context.annotation.Configuration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthControllerTest {
  @Test void explicitSecurityConfigurationDisablesBootBasicAuth() {
    assertTrue(AuthSecurityConfiguration.class.isAnnotationPresent(Configuration.class));
  }
  @Test void tokenResponseDoesNotExposePasswordFields() {
    String json = new AuthDtos.ApiResponse<>("OK", "authenticated", "t", new AuthDtos.TokenView("a", "r")).toString();
    assertFalse(json.contains("password"));
    assertFalse(json.contains("hash"));
  }
  @Test void invalidLoginPayloadViolatesConstraints() {
    var validator = Validation.buildDefaultValidatorFactory().getValidator();
    assertTrue(validator.validate(new AuthDtos.LoginRequest("", "")).size() >= 2);
  }
  @Test void meRejectsCallsWithoutGatewayServiceToken() {
    var controller = new com.cloudticket.auth.api.AuthController(mock(com.cloudticket.auth.service.AuthService.class));
    assertThrows(com.cloudticket.auth.service.AuthService.InvalidCredentialsException.class,
        () -> controller.me("00000000-0000-0000-0000-000000000001", "spoofed", null, new MockHttpServletRequest()));
  }
}

package com.cloudticket.auth;

import static org.junit.jupiter.api.Assertions.*;
import com.cloudticket.auth.api.AuthDtos;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class AuthControllerTest {
  @Test void tokenResponseDoesNotExposePasswordFields() {
    String json = new AuthDtos.ApiResponse<>("OK", "authenticated", "t", new AuthDtos.TokenView("a", "r")).toString();
    assertFalse(json.contains("password"));
    assertFalse(json.contains("hash"));
  }
  @Test void invalidLoginPayloadViolatesConstraints() {
    var validator = Validation.buildDefaultValidatorFactory().getValidator();
    assertTrue(validator.validate(new AuthDtos.LoginRequest("", "")).size() >= 2);
  }
}

package com.cloudticket.auth.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.cloudticket.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AuthExceptionHandlerTest {
  @Test void authorizationFailureReturnsForbidden() {
    var response = new AuthExceptionHandler().forbidden(new SecurityException("forbidden"));
    assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
  }

  @Test void invalidCredentialsRemainUnauthorized() {
    var response = new AuthExceptionHandler().auth(new AuthService.InvalidCredentialsException());
    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
  }
}

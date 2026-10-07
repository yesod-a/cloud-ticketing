package com.cloudticket.inventory.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AuthorizationExceptionHandlerTest {
  @Test
  void mapsAuthorizationFailureToForbidden() {
    var response = new AuthorizationExceptionHandler().forbidden(new SecurityException("forbidden"));
    assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
  }
}

package com.cloudticket.activity.api;

import com.cloudticket.common.security.CallerContextHolder;
import com.cloudticket.common.web.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Keeps authorization failures from leaking as generic 500 responses. */
@RestControllerAdvice
public class AuthorizationExceptionHandler {

  @ExceptionHandler(SecurityException.class)
  ResponseEntity<ApiError> forbidden(SecurityException ignored) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("FORBIDDEN", "Forbidden",
        CallerContextHolder.current().traceId()));
  }
}

package com.cloudticket.order;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class IdempotencyConflictException extends IllegalStateException {
  public IdempotencyConflictException() {
    super("idempotency key reused with different request");
  }
}

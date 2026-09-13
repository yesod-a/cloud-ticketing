package com.cloudticket.auth.api;
import com.cloudticket.auth.service.AuthService;
import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
@RestControllerAdvice public class AuthExceptionHandler {
 @ExceptionHandler(AuthService.DuplicateCredentialException.class) ResponseEntity<AuthDtos.ApiResponse<Void>> dup(){return ResponseEntity.status(409).body(new AuthDtos.ApiResponse<>("DUPLICATE","Phone or email already registered",null,null));}
 @ExceptionHandler({AuthService.InvalidCredentialsException.class,SecurityException.class}) ResponseEntity<AuthDtos.ApiResponse<Void>> auth(){return ResponseEntity.status(401).body(new AuthDtos.ApiResponse<>("UNAUTHORIZED","Invalid credentials",null,null));}
 @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<AuthDtos.ApiResponse<Void>> invalid(Exception e){return ResponseEntity.status(422).body(new AuthDtos.ApiResponse<>("INVALID_INPUT",e.getMessage(),null,null));}
}

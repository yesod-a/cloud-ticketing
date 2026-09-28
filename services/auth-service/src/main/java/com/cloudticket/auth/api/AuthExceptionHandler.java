package com.cloudticket.auth.api;
import com.cloudticket.auth.service.AuthService;
import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import org.springframework.web.bind.MethodArgumentNotValidException;
@RestControllerAdvice public class AuthExceptionHandler {
 @ExceptionHandler(AuthService.DuplicateCredentialException.class) ResponseEntity<AuthDtos.ApiResponse<Void>> dup(){return ResponseEntity.status(409).body(new AuthDtos.ApiResponse<>("DUPLICATE","Phone or email already registered",null,null));}
 @ExceptionHandler(AuthService.InvalidCredentialsException.class) ResponseEntity<AuthDtos.ApiResponse<Void>> auth(AuthService.InvalidCredentialsException ex){return ResponseEntity.status(401).body(new AuthDtos.ApiResponse<>("UNAUTHORIZED","Invalid credentials",null,null));}
 @ExceptionHandler(AuthService.AvatarNotFoundException.class) ResponseEntity<Void> avatarNotFound(){return ResponseEntity.notFound().build();}
 @ExceptionHandler(SecurityException.class) ResponseEntity<AuthDtos.ApiResponse<Void>> forbidden(SecurityException ex){return ResponseEntity.status(403).body(new AuthDtos.ApiResponse<>("FORBIDDEN","Forbidden",null,null));}
 @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<AuthDtos.ApiResponse<Void>> invalid(Exception e){return ResponseEntity.status(422).body(new AuthDtos.ApiResponse<>("INVALID_INPUT",e.getMessage(),null,null));}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<AuthDtos.ApiResponse<Void>> validation(){return ResponseEntity.status(422).body(new AuthDtos.ApiResponse<>("INVALID_INPUT","Invalid request",null,null));}
}

package com.cloudticket.auth.api;

import com.cloudticket.auth.persistence.entity.AuthUserEntity;
import com.cloudticket.auth.security.TokenService;
import com.cloudticket.auth.service.AuthService;
import com.cloudticket.common.security.RequireInternalToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/register")
  public ResponseEntity<AuthDtos.ApiResponse<AuthDtos.UserView>> register(
      @Valid @RequestBody AuthDtos.RegisterRequest request, HttpServletRequest http) {
    return ResponseEntity.ok(new AuthDtos.ApiResponse<>("OK", "registered", trace(http),
        view(auth.register(request.phone(), request.email(), request.password(), request.nickname()))));
  }

  @PostMapping("/login")
  public ResponseEntity<AuthDtos.ApiResponse<AuthDtos.TokenView>> login(
      @Valid @RequestBody AuthDtos.LoginRequest request, HttpServletRequest http) {
    TokenService.Issued issued = auth.login(request.identifier(), request.password());
    return ResponseEntity.ok(new AuthDtos.ApiResponse<>("OK", "authenticated", trace(http),
        new AuthDtos.TokenView(issued.accessToken(), issued.refreshToken())));
  }

  @PostMapping("/refresh")
  public ResponseEntity<AuthDtos.ApiResponse<AuthDtos.TokenView>> refresh(
      @Valid @RequestBody AuthDtos.RefreshRequest request, HttpServletRequest http) {
    TokenService.Issued issued = auth.refresh(request.refreshToken());
    return ResponseEntity.ok(new AuthDtos.ApiResponse<>("OK", "refreshed", trace(http),
        new AuthDtos.TokenView(issued.accessToken(), issued.refreshToken())));
  }

  @PostMapping("/logout")
  public ResponseEntity<AuthDtos.ApiResponse<Void>> logout(
      @Valid @RequestBody AuthDtos.LogoutRequest request, HttpServletRequest http) {
    auth.logout(request.refreshToken(), request.accessToken());
    return ResponseEntity.ok(new AuthDtos.ApiResponse<>("OK", "logged out", trace(http), null));
  }

  @PostMapping("/password/forgot")
  public ResponseEntity<AuthDtos.ApiResponse<Void>> forgot(
      @Valid @RequestBody AuthDtos.PasswordForgotRequest request, HttpServletRequest http) {
    auth.forgotPassword(request.identifier());
    return ResponseEntity.ok(new AuthDtos.ApiResponse<>("OK",
        "If the account exists, reset instructions were sent", trace(http), null));
  }

  @PostMapping("/password/reset")
  public ResponseEntity<AuthDtos.ApiResponse<AuthDtos.UserView>> reset(
      @Valid @RequestBody AuthDtos.PasswordResetRequest request, HttpServletRequest http) {
    return ResponseEntity.ok(new AuthDtos.ApiResponse<>("OK", "password reset", trace(http),
        view(auth.resetPassword(request.token(), request.password()))));
  }

  /**
   * Identity lookup for the gateway. The caller must present the internal service token, which
   * {@code @RequireInternalToken} enforces before the method is entered.
   */
  @RequireInternalToken
  @GetMapping("/me")
  public ResponseEntity<AuthDtos.ApiResponse<AuthDtos.UserView>> me(
      @RequestHeader(value = "X-User-Id", required = false) String trustedUserId,
      Authentication authentication, HttpServletRequest http) {
    String userId = trustedUserId;
    if (userId == null || userId.isBlank()) {
      userId = authentication == null ? null : authentication.getName();
    }
    if (userId == null || userId.isBlank()) throw new AuthService.InvalidCredentialsException();
    try {
      return ResponseEntity.ok(new AuthDtos.ApiResponse<>("OK", "ok", trace(http),
          view(auth.me(UUID.fromString(userId)))));
    } catch (IllegalArgumentException notAUuid) {
      throw new AuthService.InvalidCredentialsException();
    }
  }

  private static String trace(HttpServletRequest request) {
    String trace = request.getHeader("X-Trace-Id");
    return trace == null ? "" : trace;
  }

  private static AuthDtos.UserView view(AuthUserEntity user) {
    return new AuthDtos.UserView(user.getId().toString(), user.getPhone(), user.getEmail(),
        user.getNickname(), user.getStatus());
  }
}

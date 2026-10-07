package com.cloudticket.auth.api;

import com.cloudticket.auth.persistence.entity.AuthUserEntity;
import com.cloudticket.auth.security.TokenService;
import com.cloudticket.auth.service.AuthService;
import com.cloudticket.common.security.RequireInternalToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.Arrays;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

  @RequireInternalToken
  @PatchMapping("/me")
  public ResponseEntity<AuthDtos.ApiResponse<AuthDtos.UserView>> updateProfile(
      @Valid @RequestBody AuthDtos.ProfileUpdateRequest request,
      @RequestHeader(value = "X-User-Id", required = false) String trustedUserId,
      HttpServletRequest http) {
    UUID userId = userId(trustedUserId, null);
    return ResponseEntity.ok(new AuthDtos.ApiResponse<>("OK", "profile updated", trace(http),
        view(auth.updateNickname(userId, request.nickname()))));
  }

  @RequireInternalToken
  @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<AuthDtos.ApiResponse<AuthDtos.UserView>> uploadAvatar(
      @RequestPart("file") MultipartFile file,
      @RequestHeader(value = "X-User-Id", required = false) String trustedUserId,
      HttpServletRequest http) {
    UUID userId = userId(trustedUserId, null);
    return ResponseEntity.ok(new AuthDtos.ApiResponse<>("OK", "avatar updated", trace(http),
        view(auth.updateAvatar(userId, file))));
  }

  @RequireInternalToken
  @PostMapping("/me/password")
  public ResponseEntity<AuthDtos.ApiResponse<Void>> changePassword(
      @Valid @RequestBody AuthDtos.PasswordChangeRequest request,
      @RequestHeader(value = "X-User-Id", required = false) String trustedUserId,
      HttpServletRequest http) {
    UUID userId = userId(trustedUserId, null);
    auth.changePassword(userId, request.currentPassword(), request.newPassword());
    return ResponseEntity.ok(new AuthDtos.ApiResponse<>("OK", "password changed", trace(http), null));
  }

  @GetMapping("/avatars/{userId}")
  public ResponseEntity<ByteArrayResource> avatar(@org.springframework.web.bind.annotation.PathVariable UUID userId)
      throws IOException {
    var stored = auth.openAvatar(userId).orElseThrow(AuthService.AvatarNotFoundException::new);
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
        .contentType(MediaType.parseMediaType(stored.contentType()))
        .body(new ByteArrayResource(stored.readBytes()));
  }

  @GetMapping("/public-profiles")
  public ResponseEntity<AuthDtos.ApiResponse<List<AuthDtos.PublicProfileView>>> publicProfiles(
      @RequestParam(defaultValue = "") String ids, HttpServletRequest http) {
    List<UUID> userIds = Arrays.stream(ids.split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .flatMap(value -> {
          try { return java.util.stream.Stream.of(UUID.fromString(value)); }
          catch (IllegalArgumentException ignored) { return java.util.stream.Stream.empty(); }
        })
        .distinct().limit(100).toList();
    List<AuthDtos.PublicProfileView> profiles = auth.publicProfiles(userIds).stream()
        .map(user -> new AuthDtos.PublicProfileView(user.getId().toString(), user.getNickname(), avatarUrl(user)))
        .toList();
    return ResponseEntity.ok(new AuthDtos.ApiResponse<>("OK", "ok", trace(http), profiles));
  }

  private static String trace(HttpServletRequest request) {
    String trace = request.getHeader("X-Trace-Id");
    return trace == null ? "" : trace;
  }

  private static UUID userId(String trustedUserId, Authentication authentication) {
    String value = trustedUserId;
    if (value == null || value.isBlank()) value = authentication == null ? null : authentication.getName();
    if (value == null || value.isBlank()) throw new AuthService.InvalidCredentialsException();
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException malformed) {
      throw new AuthService.InvalidCredentialsException();
    }
  }

  private static AuthDtos.UserView view(AuthUserEntity user) {
    String avatarUrl = avatarUrl(user);
    return new AuthDtos.UserView(user.getId().toString(), user.getPhone(), user.getEmail(),
        user.getNickname(), user.getStatus(), avatarUrl, user.getCreatedAt());
  }

  private static String avatarUrl(AuthUserEntity user) {
    if (user.getAvatarFilename() == null || user.getId() == null) return null;
    Instant version = user.getUpdatedAt() == null ? user.getCreatedAt() : user.getUpdatedAt();
    return "/api/auth/avatars/" + user.getId() + (version == null ? "" : "?v=" + version.toEpochMilli());
  }
}

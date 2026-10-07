package com.cloudticket.auth.api;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
public final class AuthDtos {
 private AuthDtos(){}
 public record RegisterRequest(String phone,String email,@NotBlank @Size(min=8,max=128) String password,String nickname){}
 public record LoginRequest(@NotBlank String identifier,@NotBlank String password){}
 public record RefreshRequest(@NotBlank String refreshToken){}
 public record LogoutRequest(@NotBlank String refreshToken,String accessToken){}
 public record PasswordForgotRequest(@NotBlank String identifier){}
 public record PasswordResetRequest(@NotBlank String token,@NotBlank @Size(min=8,max=128) String password){}
 public record UserView(String id,String phone,String email,String nickname,String status,
                        String avatarUrl, Instant createdAt) {
  public UserView(String id,String phone,String email,String nickname,String status) {
   this(id, phone, email, nickname, status, null, null);
  }
 }
 public record PublicProfileView(String id,String nickname,String avatarUrl){}
 public record ProfileUpdateRequest(@Size(max=120) String nickname){}
 public record PasswordChangeRequest(@NotBlank String currentPassword,
                                     @NotBlank @Size(min=8,max=128) String newPassword){}
 public record TokenView(String accessToken,String refreshToken){}
 public record ApiResponse<T>(String code,String message,String traceId,T data){}
}

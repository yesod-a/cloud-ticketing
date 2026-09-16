package com.cloudticket.auth.api;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public final class AuthDtos {
 private AuthDtos(){}
 public record RegisterRequest(String phone,String email,@NotBlank @Size(min=8,max=128) String password,String nickname){}
 public record LoginRequest(@NotBlank String identifier,@NotBlank String password){}
 public record RefreshRequest(@NotBlank String refreshToken){}
 public record LogoutRequest(@NotBlank String refreshToken,String accessToken){}
 public record PasswordForgotRequest(@NotBlank String identifier){}
 public record PasswordResetRequest(@NotBlank String token,@NotBlank @Size(min=8,max=128) String password){}
 public record UserView(String id,String phone,String email,String nickname,String status){}
 public record TokenView(String accessToken,String refreshToken){}
 public record ApiResponse<T>(String code,String message,String traceId,T data){}
}

package com.cloudticket.auth.api;
public final class AuthDtos {
 private AuthDtos(){}
 public record RegisterRequest(String phone,String email,String password,String nickname){}
 public record LoginRequest(String identifier,String password){}
 public record RefreshRequest(String refreshToken){}
 public record PasswordForgotRequest(String identifier){}
 public record PasswordResetRequest(String token,String password){}
 public record UserView(String id,String phone,String email,String nickname,String status){}
 public record TokenView(String accessToken,String refreshToken){}
 public record ApiResponse<T>(String code,String message,String traceId,T data){}
}

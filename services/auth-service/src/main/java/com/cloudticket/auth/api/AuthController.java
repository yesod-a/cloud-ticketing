package com.cloudticket.auth.api;
import com.cloudticket.auth.domain.UserEntity;
import com.cloudticket.auth.security.TokenService;
import com.cloudticket.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;

@RestController @RequestMapping("/api/auth")
public class AuthController {
 private final AuthService auth; private final String internalServiceToken;
 public AuthController(AuthService auth){this(auth,"dev-internal-token");}
 @org.springframework.beans.factory.annotation.Autowired public AuthController(AuthService auth,@Value("${cloudticket.internal-service-token:dev-internal-token}") String internalServiceToken){this.auth=auth;this.internalServiceToken=internalServiceToken;}
 private String trace(HttpServletRequest r){String t=r.getHeader("X-Trace-Id"); return t==null?"":t;}
 private AuthDtos.UserView view(UserEntity u){return new AuthDtos.UserView(u.id().toString(),u.phone(),u.email(),u.nickname(),u.status());}
 @PostMapping("/register") public ResponseEntity<AuthDtos.ApiResponse<AuthDtos.UserView>> register(@Valid @RequestBody AuthDtos.RegisterRequest x,HttpServletRequest r){return ResponseEntity.ok(new AuthDtos.ApiResponse<>("OK","registered",trace(r),view(auth.register(x.phone(),x.email(),x.password(),x.nickname()))));}
 @PostMapping("/login") public ResponseEntity<AuthDtos.ApiResponse<AuthDtos.TokenView>> login(@Valid @RequestBody AuthDtos.LoginRequest x,HttpServletRequest r){var t=auth.login(x.identifier(),x.password()); return ResponseEntity.ok(new AuthDtos.ApiResponse<>("OK","authenticated",trace(r),new AuthDtos.TokenView(t.accessToken(),t.refreshToken())));}
 @PostMapping("/refresh") public ResponseEntity<AuthDtos.ApiResponse<AuthDtos.TokenView>> refresh(@Valid @RequestBody AuthDtos.RefreshRequest x,HttpServletRequest r){var t=auth.refresh(x.refreshToken()); return ResponseEntity.ok(new AuthDtos.ApiResponse<>("OK","refreshed",trace(r),new AuthDtos.TokenView(t.accessToken(),t.refreshToken())));}
 @PostMapping("/logout") public ResponseEntity<AuthDtos.ApiResponse<Void>> logout(@Valid @RequestBody AuthDtos.LogoutRequest x,HttpServletRequest r){auth.logout(x.refreshToken(),x.accessToken()); return ResponseEntity.ok(new AuthDtos.ApiResponse<>("OK","logged out",trace(r),null));}
 @PostMapping("/password/forgot") public ResponseEntity<AuthDtos.ApiResponse<Void>> forgot(@Valid @RequestBody AuthDtos.PasswordForgotRequest x,HttpServletRequest r){auth.forgotPassword(x.identifier()); return ResponseEntity.ok(new AuthDtos.ApiResponse<>("OK","If the account exists, reset instructions were sent",trace(r),null));}
 @PostMapping("/password/reset") public ResponseEntity<AuthDtos.ApiResponse<AuthDtos.UserView>> reset(@Valid @RequestBody AuthDtos.PasswordResetRequest x,HttpServletRequest r){return ResponseEntity.ok(new AuthDtos.ApiResponse<>("OK","password reset",trace(r),view(auth.resetPassword(x.token(),x.password()))));}
 @GetMapping("/me") public ResponseEntity<AuthDtos.ApiResponse<AuthDtos.UserView>> me(
   @RequestHeader(value="X-User-Id", required=false) String trustedUserId,
   @RequestHeader(value="X-Internal-Service-Token", required=false) String serviceToken,
   Authentication authentication,
   HttpServletRequest r){
   if (serviceToken == null || !serviceToken.equals(internalServiceToken)) throw new AuthService.InvalidCredentialsException();
   String userId = trustedUserId;
   if (userId == null || userId.isBlank()) {
     userId = authentication == null ? null : authentication.getName();
   }
   if (userId == null || userId.isBlank()) throw new AuthService.InvalidCredentialsException();
   try {
     return ResponseEntity.ok(new AuthDtos.ApiResponse<>("OK","ok",trace(r),view(auth.me(UUID.fromString(userId)))));
   } catch (IllegalArgumentException ex) {
     throw new AuthService.InvalidCredentialsException();
   }
 }
}

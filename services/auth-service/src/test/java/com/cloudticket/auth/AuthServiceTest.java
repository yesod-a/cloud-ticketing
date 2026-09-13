package com.cloudticket.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cloudticket.auth.domain.UserEntity;
import com.cloudticket.auth.repository.UserRepository;
import com.cloudticket.auth.security.TokenService;
import com.cloudticket.auth.service.AuthService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthServiceTest {
  @Test void duplicateContactsAreRejected() {
    UserRepository repo = mock(UserRepository.class);
    when(repo.existsByPhone("13800138000")).thenReturn(true);
    AuthService service = new AuthService(repo, new BCryptPasswordEncoder(), mock(TokenService.class));
    assertThrows(AuthService.DuplicateCredentialException.class, () -> service.register("13800138000", null, "StrongPass1", "n"));
  }
  @Test void inactiveUsersCannotLogin() {
    UserRepository repo = mock(UserRepository.class); UUID id = UUID.randomUUID();
    when(repo.findByPhoneOrEmail(any())).thenReturn(Optional.of(new UserEntity(id,"13800138000",null,new BCryptPasswordEncoder().encode("StrongPass1"),"n","DISABLED",0,null,0,Instant.now(),Instant.now())));
    AuthService service = new AuthService(repo, new BCryptPasswordEncoder(), mock(TokenService.class));
    assertThrows(AuthService.InvalidCredentialsException.class, () -> service.login("13800138000", "StrongPass1"));
  }
}

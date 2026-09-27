package com.cloudticket.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.cloudticket.auth.persistence.mapper.AuthRoleMapper;
import com.cloudticket.auth.persistence.mapper.AuthUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AdminAccountInitializerTest {

  @Test
  void skipsInitializationWhenAdminEnvironmentIsNotConfigured() {
    AuthUserMapper users = mock(AuthUserMapper.class);
    AuthRoleMapper roles = mock(AuthRoleMapper.class);

    assertDoesNotThrow(() -> new AdminAccountInitializer(users, roles, new BCryptPasswordEncoder()).run());

    verifyNoInteractions(users);
    verifyNoInteractions(roles);
  }
}

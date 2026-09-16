package com.cloudticket.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AdminAccountInitializerTest {
  @Test void skipsInitializationWhenAdminEnvironmentIsNotConfigured() {
    var jdbc = mock(JdbcTemplate.class);
    assertDoesNotThrow(() -> new AdminAccountInitializer(jdbc, new BCryptPasswordEncoder()).run());
    verifyNoInteractions(jdbc);
  }
}

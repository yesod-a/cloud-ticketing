package com.cloudticket.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.cloudticket.auth.security.TokenService;
import com.cloudticket.auth.repository.RefreshTokenRepository;

@SpringBootApplication
public class AuthApplication {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean TokenService tokenService(RefreshTokenRepository repository) { return new TokenService(repository); }
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}

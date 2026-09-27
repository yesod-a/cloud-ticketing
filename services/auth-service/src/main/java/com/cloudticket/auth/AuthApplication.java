package com.cloudticket.auth;

import com.cloudticket.auth.persistence.mapper.AuthRefreshTokenMapper;
import com.cloudticket.auth.security.TokenService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
@MapperScan("com.cloudticket.auth.persistence.mapper")
public class AuthApplication {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean TokenService tokenService(AuthRefreshTokenMapper repository) { return new TokenService(repository); }
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}

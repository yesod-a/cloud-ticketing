package com.cloudticket.auth.persistence;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.cloudticket.common.mybatis.handler.ByteArrayUuidTypeHandler;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link UUID} parameters and columns to the {@code BINARY(16)} layout the auth schema uses.
 *
 * <p>With the handler registered, mappers can say {@code WHERE id = #{id}} and receive a {@code UUID}
 * instead of converting with {@code UUID_TO_BIN}/{@code BIN_TO_UUID} in every statement.
 */
@Configuration
public class AuthMybatisConfiguration {

  @Bean
  ConfigurationCustomizer authUuidTypeHandler() {
    return configuration ->
        configuration.getTypeHandlerRegistry().register(UUID.class, new ByteArrayUuidTypeHandler());
  }
}

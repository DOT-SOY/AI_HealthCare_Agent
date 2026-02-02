package com.backend.config;

import com.backend.security.token.RefreshTokenService;
import com.backend.security.token.RefreshTokenRedisService;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Refresh Token 저장소 설정 (Redis 사용)
 */
@Configuration
@Log4j2
public class RefreshTokenConfig {

    @Bean
    @Primary
    public RefreshTokenService refreshTokenService(RefreshTokenRedisService redisService) {
        log.info("=== Refresh Token Storage: Redis ===");
        return redisService;
    }
}

package com.backend.config;

import com.backend.security.token.RefreshTokenService;
import com.backend.security.token.RefreshTokenRedisService;
import com.backend.security.token.RefreshTokenDBService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Refresh Token 저장소 선택 설정
 *
 * 사용 방법:
 * application.properties에 다음 중 하나 설정:
 * refresh.token.storage=redis  (기본값, Redis 사용)
 * refresh.token.storage=db     (DB 사용)
 */
@Configuration
@Log4j2
public class RefreshTokenConfig {

    @Value("${refresh.token.storage:redis}")
    private String storageType;

    @Bean
    @Primary
    public RefreshTokenService refreshTokenService(
            @Qualifier("refreshTokenRedisService") RefreshTokenRedisService redisService,
            @Qualifier("refreshTokenDBService") RefreshTokenDBService dbService) {

        log.info("=== Refresh Token Storage Configuration ===");

        if ("db".equalsIgnoreCase(storageType)) {
            log.info("Selected: DB Storage");
            return dbService;
        } else {
            log.info("Selected: Redis Storage (default)");
            return redisService;
        }
    }
}


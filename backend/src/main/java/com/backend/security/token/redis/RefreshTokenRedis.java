package com.backend.security.token.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Redis에 저장되는 Refresh Token 정보
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRedis implements Serializable {
    private String refreshTokenId; // jti
    private String refreshToken; // JWT 토큰 자체 (무결성 검증용)
    private String userId; // email과 동일
    private String userEmail;
    private String familyId;
    private boolean used; // 사용 여부 (재발급 시 true로 변경)
    private String userAgentHash;
    private String ipAddress; // IP 힌트 (예: /24, /64 prefix)
}

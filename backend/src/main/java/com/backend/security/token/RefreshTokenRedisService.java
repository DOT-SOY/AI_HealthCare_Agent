package com.backend.security.token;

import com.backend.security.token.redis.RefreshTokenRedis;
import com.backend.security.token.ClientContextUtil;
import com.backend.util.CustomJWTException;
import com.backend.util.JWTUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service("refreshTokenRedisService")
@RequiredArgsConstructor
@Log4j2
public class RefreshTokenRedisService implements RefreshTokenService {

    private static final String TOKEN_KEY_PREFIX = "refresh:token:";
    private static final String FAMILY_KEY_PREFIX = "refresh:family:";
    private static final int REFRESH_TTL_MIN = 60 * 24; // 24시간
    private static final int REFRESH_COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24;

    private final RedisTemplate<String, Object> redisTemplate;

    public int refreshCookieMaxAgeSeconds() {
        return REFRESH_COOKIE_MAX_AGE_SECONDS;
    }

    /**
     * 새 세션(토큰 패밀리) 생성 및 첫 Refresh Token 발급
     */
    public String issueNewSessionRefreshToken(String email, HttpServletRequest request, String amr) {
        String familyId = UUID.randomUUID().toString();
        return issueRefreshToken(email, familyId, request, amr);
    }

    /**
     * Refresh Token 회전 (재발급)
     * used 플래그 기반 Replay 탐지
     */
    public String rotateRefreshToken(String refreshTokenRaw, HttpServletRequest request, String expectedEmailFromAccessOrNull) {
        // JWT 파싱 (성능 측정에서 제외 - 동일한 작업)
        Map<String, Object> claims = JWTUtil.validateToken(refreshTokenRaw);
        String tokenType = (String) claims.get("tokenType");

        if (!TokenType.REFRESH.name().equals(tokenType)) {
            throw new CustomJWTException("INVALID_TOKEN_TYPE");
        }

        String jti = (String) claims.get("jti");
        String familyId = (String) claims.get("familyId");
        String email = (String) claims.get("email");

        if (jti == null || familyId == null || email == null) {
            throw new CustomJWTException("INVALID_REFRESH_CLAIMS");
        }

        // Access Token과 Refresh Token의 주체 일치 검증
        if (expectedEmailFromAccessOrNull != null && !expectedEmailFromAccessOrNull.equals(email)) {
            revokeFamily(familyId);
            throw new CustomJWTException("REFRESH_BINDING_MISMATCH");
        }

        // ========== Redis 조회 (성능 측정 구간) ==========
        // 최적화: 키 생성과 조회를 한 번에 수행
        String tokenKey = TOKEN_KEY_PREFIX + jti;
        RefreshTokenRedis stored = (RefreshTokenRedis) redisTemplate.opsForValue().get(tokenKey);
        // ================================================

        if (stored == null) {
            throw new CustomJWTException("UNKNOWN_REFRESH");
        }

        // Replay 탐지: used == true인 토큰 재사용 시도
        if (stored.isUsed()) {
            log.warn("Replay attack detected: used token presented. jti={}, familyId={}, email={}", jti, familyId, email);
            revokeFamily(familyId);
            throw new CustomJWTException("REFRESH_REPLAY_DETECTED");
        }

        // 토큰 무결성 검증 (Redis에 저장된 토큰과 비교)
        if (!refreshTokenRaw.equals(stored.getRefreshToken())) {
            log.warn("Token tampering detected: token mismatch. jti={}, familyId={}, email={}", jti, familyId, email);
            revokeFamily(familyId);
            throw new CustomJWTException("REFRESH_TAMPERED");
        }

        // User-Agent 검증
        ClientContext ctx = ClientContextUtil.from(request);
        if (!ctx.getUserAgentHash().equals(stored.getUserAgentHash())) {
            log.warn("Device mismatch detected: UA hash mismatch. familyId={}, email={}", familyId, email);
            revokeFamily(familyId);
            throw new CustomJWTException("REFRESH_DEVICE_MISMATCH");
        }

        // IP 검증 (best-effort)
        if (stored.getIpAddress() != null && ctx.getIpHint() != null
                && !stored.getIpAddress().equals(ctx.getIpHint())) {
            log.warn("IP mismatch detected: IP hint mismatch. familyId={}, email={}", familyId, email);
            revokeFamily(familyId);
            throw new CustomJWTException("REFRESH_IP_MISMATCH");
        }

        // 기존 토큰을 used=true로 변경 (삭제하지 않음) - 단일 업데이트이므로 빠름
        stored.setUsed(true);
        Duration refreshTtl = Duration.ofMinutes(REFRESH_TTL_MIN);
        redisTemplate.opsForValue().set(tokenKey, stored, refreshTtl);

        // 새 Refresh Token 발급 (같은 familyId)
        String amr = (String) claims.getOrDefault("amr", "unknown");
        return issueRefreshToken(email, familyId, request, amr);
    }

    /**
     * 특정 familyId에 속한 모든 Refresh Token 삭제 (Replay 공격 대응)
     * 최적화: 여러 키를 한 번에 삭제하여 네트워크 왕복 최소화
     */
    public void revokeFamily(String familyId) {
        if (familyId == null || familyId.isBlank()) return;

        String familyKey = FAMILY_KEY_PREFIX + familyId;
        Set<Object> jtiSet = redisTemplate.opsForSet().members(familyKey);

        if (jtiSet != null && !jtiSet.isEmpty()) {
            // 여러 키를 한 번에 삭제 (네트워크 왕복 최소화)
            String[] keysToDelete = new String[jtiSet.size() + 1];
            int index = 0;
            for (Object jtiObj : jtiSet) {
                String jti = jtiObj.toString();
                keysToDelete[index++] = TOKEN_KEY_PREFIX + jti;
            }
            keysToDelete[index] = familyKey;

            // 한 번의 호출로 여러 키 삭제
            List<String> keysList = Arrays.asList(keysToDelete);
            redisTemplate.delete(keysList);

            log.info("Family revoked: familyId={}, tokensDeleted={}", familyId, jtiSet.size());
        }
    }

    /**
     * 특정 사용자의 모든 Refresh Token Family 삭제
     * 비밀번호 변경 시 모든 기기에서 로그아웃 처리
     *
     * Redis 구현: 모든 토큰 키를 스캔하여 해당 사용자의 토큰을 찾고, Family를 폐기
     * 주의: 대량의 토큰이 있는 경우 성능 저하 가능 (실무에서는 사용자별 인덱스 Set을 별도로 관리 권장)
     */
    @Override
    public void revokeAllFamiliesForUser(String email) {
        if (email == null || email.isBlank()) return;

        // Redis에서 모든 토큰 키 패턴으로 검색
        Set<String> allTokenKeys = redisTemplate.keys(TOKEN_KEY_PREFIX + "*");
        if (allTokenKeys == null || allTokenKeys.isEmpty()) {
            log.info("No tokens found for user revocation: email={}", email);
            return;
        }

        Set<String> familiesToRevoke = new java.util.HashSet<>();

        // 각 토큰을 확인하여 해당 사용자의 토큰인지 확인
        for (String tokenKey : allTokenKeys) {
            try {
                RefreshTokenRedis tokenData = (RefreshTokenRedis) redisTemplate.opsForValue().get(tokenKey);
                if (tokenData != null && email.equals(tokenData.getUserEmail())) {
                    familiesToRevoke.add(tokenData.getFamilyId());
                }
            } catch (Exception e) {
                log.warn("Error reading token data from Redis: key={}, error={}", tokenKey, e.getMessage());
            }
        }

        // 찾은 모든 Family 폐기
        int revokedCount = 0;
        for (String familyId : familiesToRevoke) {
            revokeFamily(familyId);
            revokedCount++;
        }

        log.info("All families revoked for user: email={}, familiesRevoked={}", email, revokedCount);
    }

    /**
     * 새 Refresh Token 발급 및 Redis 저장
     */
    private String issueRefreshToken(String email, String familyId, HttpServletRequest request, String amr) {
        String jti = UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();

        // JWT 생성
        Map<String, Object> refreshClaims = Map.of(
                "email", email,
                "tokenType", TokenType.REFRESH.name(),
                "jti", jti,
                "familyId", familyId,
                "auth_time", now.getEpochSecond(),
                "amr", amr == null ? "pwd" : amr
        );

        String refreshJwt = JWTUtil.generateToken(refreshClaims, REFRESH_TTL_MIN);

        // Redis 저장 정보 생성
        ClientContext ctx = ClientContextUtil.from(request);
        RefreshTokenRedis tokenData = RefreshTokenRedis.builder()
                .refreshTokenId(jti)
                .refreshToken(refreshJwt)
                .userId(email)
                .userEmail(email)
                .familyId(familyId)
                .used(false)
                .userAgentHash(ctx.getUserAgentHash())
                .ipAddress(ctx.getIpHint())
                .build();

        // Redis 저장 - 최적화: TTL을 함께 설정하여 네트워크 왕복 최소화
        String tokenKey = TOKEN_KEY_PREFIX + jti;
        String familyKey = FAMILY_KEY_PREFIX + familyId;
        Duration refreshTtl = Duration.ofMinutes(REFRESH_TTL_MIN);

        // 토큰 저장 (TTL 포함) - 한 번의 호출로 저장과 TTL 설정
        redisTemplate.opsForValue().set(tokenKey, tokenData, refreshTtl);

        // Family Set에 추가 및 TTL 설정 - 최적화: TTL이 필요할 때만 설정
        Long addedCount = redisTemplate.opsForSet().add(familyKey, jti);
        // 새로 추가된 경우(addedCount > 0) 또는 TTL이 없는 경우에만 TTL 설정
        Long expireTime = redisTemplate.getExpire(familyKey);
        if ((addedCount != null && addedCount > 0) || (expireTime != null && expireTime == -1)) {
            redisTemplate.expire(familyKey, refreshTtl);
        }

        return refreshJwt;
    }
}

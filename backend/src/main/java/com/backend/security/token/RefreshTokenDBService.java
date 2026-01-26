package com.backend.security.token;

import com.backend.domain.member.RefreshToken;
import com.backend.repository.member.RefreshTokenRepository;
import com.backend.common.exception.JWTException;
import com.backend.common.exception.ErrorCode;
import com.backend.util.JWTUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service("refreshTokenDBService")
@RequiredArgsConstructor
@Log4j2
public class RefreshTokenDBService implements RefreshTokenService {

    private static final int REFRESH_TTL_MIN = 60 * 24; // 24시간
    private static final int REFRESH_COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24;

    private final RefreshTokenRepository refreshTokenRepository;

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
    @Transactional
    public String rotateRefreshToken(String refreshTokenRaw, HttpServletRequest request, String expectedEmailFromAccessOrNull) {
        // JWT 파싱 (성능 측정에서 제외 - 동일한 작업)
        Map<String, Object> claims = JWTUtil.validateToken(refreshTokenRaw);
        String tokenType = (String) claims.get("tokenType");

        if (!TokenType.REFRESH.name().equals(tokenType)) {
            throw new JWTException(ErrorCode.JWT_INVALID_TOKEN_TYPE);
        }

        String jti = (String) claims.get("jti");
        String familyId = (String) claims.get("familyId");
        String email = (String) claims.get("email");

        if (jti == null || familyId == null || email == null) {
            throw new JWTException(ErrorCode.JWT_INVALID_REFRESH_CLAIMS);
        }

        // Access Token과 Refresh Token의 주체 일치 검증
        if (expectedEmailFromAccessOrNull != null && !expectedEmailFromAccessOrNull.equals(email)) {
            revokeFamily(familyId);
            throw new JWTException(ErrorCode.JWT_REFRESH_BINDING_MISMATCH);
        }

        // ========== DB 조회 (성능 측정 구간) ==========
        RefreshToken stored = refreshTokenRepository.findByRefreshTokenId(jti)
                .orElseThrow(() -> new JWTException(ErrorCode.JWT_UNKNOWN_REFRESH));
        // ================================================

        // 만료된 토큰 처리
        if (stored.isExpired()) {
            revokeFamily(familyId);
            throw new JWTException(ErrorCode.JWT_UNKNOWN_REFRESH);
        }

        // Replay 탐지: used == true인 토큰 재사용 시도
        if (stored.getUsed()) {
            log.warn("Replay attack detected: used token presented. jti={}, familyId={}, email={}", jti, familyId, email);
            revokeFamily(familyId);
            throw new JWTException(ErrorCode.JWT_REFRESH_REPLAY_DETECTED);
        }

        // 토큰 무결성 검증 (DB에 저장된 토큰과 비교)
        if (!refreshTokenRaw.equals(stored.getRefreshToken())) {
            log.warn("Token tampering detected: token mismatch. jti={}, familyId={}, email={}", jti, familyId, email);
            revokeFamily(familyId);
            throw new JWTException(ErrorCode.JWT_REFRESH_TAMPERED);
        }

        // User-Agent 검증
        ClientContext ctx = ClientContextUtil.from(request);
        if (!ctx.getUserAgentHash().equals(stored.getUserAgentHash())) {
            log.warn("Device mismatch detected: UA hash mismatch. familyId={}, email={}", familyId, email);
            revokeFamily(familyId);
            throw new JWTException(ErrorCode.JWT_REFRESH_DEVICE_MISMATCH);
        }

        // IP 검증 (best-effort)
        if (stored.getIpAddress() != null && ctx.getIpHint() != null
                && !stored.getIpAddress().equals(ctx.getIpHint())) {
            log.warn("IP mismatch detected: IP hint mismatch. familyId={}, email={}", familyId, email);
            revokeFamily(familyId);
            throw new JWTException(ErrorCode.JWT_REFRESH_IP_MISMATCH);
        }

        // 기존 토큰을 used=true로 변경 (삭제하지 않음)
        stored.markAsUsed();
        refreshTokenRepository.save(stored);

        // 새 Refresh Token 발급 (같은 familyId)
        String amr = (String) claims.getOrDefault("amr", "unknown");
        return issueRefreshToken(email, familyId, request, amr);
    }

    /**
     * 특정 familyId에 속한 모든 Refresh Token 삭제 (Replay 공격 대응)
     */
    @Transactional
    public void revokeFamily(String familyId) {
        if (familyId == null || familyId.isBlank()) return;
        refreshTokenRepository.deleteByFamilyId(familyId);
    }

    /**
     * 특정 사용자의 모든 Refresh Token Family 삭제
     * 비밀번호 변경 시 모든 기기에서 로그아웃 처리
     */
    @Override
    @Transactional
    public void revokeAllFamiliesForUser(String email) {
        if (email == null || email.isBlank()) return;

        // 사용자 이메일로 모든 Refresh Token 삭제
        // 인덱스(idx_refresh_token_email)를 사용하여 효율적으로 조회/삭제
        refreshTokenRepository.deleteByUserEmail(email);

        log.info("All refresh tokens revoked for user: email={}", email);
    }

    /**
     * 새 Refresh Token 발급 및 DB 저장
     */
    @Transactional
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

        // DB 저장
        ClientContext ctx = ClientContextUtil.from(request);
        Instant expiresAt = now.plus(Duration.ofMinutes(REFRESH_TTL_MIN));

        RefreshToken tokenEntity = RefreshToken.builder()
                .refreshTokenId(jti)
                .refreshToken(refreshJwt)
                .userEmail(email)
                .familyId(familyId)
                .used(false)
                .userAgentHash(ctx.getUserAgentHash())
                .ipAddress(ctx.getIpHint())
                .expiresAt(expiresAt)
                .build();

        refreshTokenRepository.save(tokenEntity);

        return refreshJwt;
    }
}


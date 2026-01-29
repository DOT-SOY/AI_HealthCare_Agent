package com.backend.security.token;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Refresh Token 서비스 인터페이스
 * Redis와 DB 구현체를 통일된 인터페이스로 제공
 */
public interface RefreshTokenService {
    /**
     * 새 세션(토큰 패밀리) 생성 및 첫 Refresh Token 발급
     */
    String issueNewSessionRefreshToken(String email, HttpServletRequest request, String amr);

    /**
     * Refresh Token 회전 (재발급)
     */
    String rotateRefreshToken(String refreshTokenRaw, HttpServletRequest request, String expectedEmailFromAccessOrNull);

    /**
     * 특정 familyId에 속한 모든 Refresh Token 삭제
     */
    void revokeFamily(String familyId);

    /**
     * 특정 사용자의 모든 Refresh Token Family 삭제
     * 비밀번호 변경 시 모든 기기에서 로그아웃 처리에 사용
     *
     * @param email 사용자 이메일
     */
    void revokeAllFamiliesForUser(String email);

    /**
     * 쿠키 Max-Age (초 단위)
     */
    int refreshCookieMaxAgeSeconds();
}


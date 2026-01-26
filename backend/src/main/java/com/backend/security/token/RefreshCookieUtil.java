package com.backend.security.token;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;

public class RefreshCookieUtil {
    public static final String COOKIE_NAME = "refreshToken";
    private static final String COOKIE_PATH = "/";

    private RefreshCookieUtil() {}

    /**
     * Refresh Token 쿠키 설정
     * ResponseCookie를 사용하여 Set-Cookie 헤더를 한 번만 설정
     *
     * @param request HttpServletRequest
     * @param response HttpServletResponse
     * @param refreshToken Refresh Token 값
     * @param maxAgeSeconds 쿠키 만료 시간 (초)
     */
    public static void set(HttpServletRequest request, HttpServletResponse response, String refreshToken, int maxAgeSeconds) {
        // 응답이 커밋되지 않았는지 확인
        if (response.isCommitted()) {
            throw new IllegalStateException("Response already committed. Cannot set cookie.");
        }

        // Refresh Token null 체크
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token cannot be null or blank");
        }

        // 환경에 따라 Secure와 SameSite 동적 설정
        // HTTPS: Secure=true + SameSite=None
        // HTTP (로컬 개발): Secure=false + SameSite=Lax
        boolean isSecure = request.isSecure();
        String sameSite = isSecure ? "None" : "Lax";

        // ResponseCookie 생성
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, refreshToken)
                .path(COOKIE_PATH)
                .maxAge(maxAgeSeconds)
                .httpOnly(true)
                .secure(isSecure)
                .sameSite(sameSite)
                .build();

        // Set-Cookie 헤더를 한 번만 설정
        response.addHeader("Set-Cookie", cookie.toString());
    }

    /**
     * Refresh Token 쿠키 삭제
     * 동일한 name, path, secure, samesite로 Max-Age=0으로 즉시 삭제
     *
     * @param request HttpServletRequest
     * @param response HttpServletResponse
     */
    public static void clear(HttpServletRequest request, HttpServletResponse response) {
        // 환경에 따라 Secure와 SameSite 동적 설정 (set()과 동일하게)
        boolean isSecure = request.isSecure();
        String sameSite = isSecure ? "None" : "Lax";


        // ResponseCookie 생성 (Max-Age=0으로 즉시 삭제)
        // set()에서 설정한 것과 동일한 Secure, SameSite 속성 사용
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
                .path(COOKIE_PATH)
                .maxAge(0)
                .httpOnly(true)
                .secure(isSecure)
                .sameSite(sameSite)
                .build();

        // Set-Cookie 헤더 설정
        response.addHeader("Set-Cookie", cookie.toString());

    }

    /**
     * Refresh Token 쿠키 읽기
     *
     * @param request HttpServletRequest
     * @return Refresh Token 값 (없으면 null)
     */
    public static String get(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}



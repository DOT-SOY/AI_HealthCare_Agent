package com.backend.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 쿠키 처리 유틸리티
 */
@Component
public class CookieUtil {
    
    private static final String GUEST_TOKEN_COOKIE_NAME = "guest_token";
    private static final int COOKIE_MAX_AGE = 60 * 60 * 24 * 30; // 30일
    private static final String COOKIE_PATH = "/";
    
    /**
     * 게스트 토큰 쿠키 읽기
     * 
     * @param request HTTP 요청
     * @return 게스트 토큰 (없으면 null)
     */
    public String getGuestToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        
        for (Cookie cookie : cookies) {
            if (GUEST_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        
        return null;
    }
    
    /**
     * 게스트 토큰 쿠키 설정 (HTTP-Only, SameSite=Lax)
     * 
     * @param request HTTP 요청 (Secure 설정용)
     * @param response HTTP 응답
     * @param guestToken 게스트 토큰
     */
    public void setGuestToken(HttpServletRequest request, HttpServletResponse response, String guestToken) {
        // 환경에 따라 Secure 동적 설정
        // HTTPS: Secure=true + SameSite=None
        // HTTP (로컬 개발): Secure=false + SameSite=Lax
        boolean isSecure = request.isSecure();
        String sameSite = isSecure ? "None" : "Lax";
        
        // ResponseCookie 생성
        ResponseCookie cookie = ResponseCookie.from(GUEST_TOKEN_COOKIE_NAME, guestToken)
                .path(COOKIE_PATH)
                .maxAge(COOKIE_MAX_AGE)
                .httpOnly(true)
                .secure(isSecure)
                .sameSite(sameSite)
                .build();
        
        // Set-Cookie 헤더 설정
        response.addHeader("Set-Cookie", cookie.toString());
    }
    
    /**
     * 게스트 토큰 쿠키 삭제
     * 
     * @param request HTTP 요청 (Secure 설정용)
     * @param response HTTP 응답
     */
    public void removeGuestToken(HttpServletRequest request, HttpServletResponse response) {
        // 환경에 따라 Secure와 SameSite 동적 설정 (setGuestToken과 동일하게)
        boolean isSecure = request.isSecure();
        String sameSite = isSecure ? "None" : "Lax";
        
        // ResponseCookie 생성 (Max-Age=0으로 즉시 삭제)
        ResponseCookie cookie = ResponseCookie.from(GUEST_TOKEN_COOKIE_NAME, "")
                .path(COOKIE_PATH)
                .maxAge(0)
                .httpOnly(true)
                .secure(isSecure)
                .sameSite(sameSite)
                .build();
        
        // Set-Cookie 헤더 설정
        response.addHeader("Set-Cookie", cookie.toString());
    }
}

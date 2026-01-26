package com.backend.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

/**
 * 쿠키 처리 유틸리티
 */
@Component
public class CookieUtil {
    
    private static final String GUEST_TOKEN_COOKIE_NAME = "guest_token";
    private static final int COOKIE_MAX_AGE = 60 * 60 * 24 * 365; // 1년
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
     * 게스트 토큰 쿠키 설정 (HTTP-Only)
     * 
     * @param response HTTP 응답
     * @param guestToken 게스트 토큰
     */
    public void setGuestToken(HttpServletResponse response, String guestToken) {
        Cookie cookie = new Cookie(GUEST_TOKEN_COOKIE_NAME, guestToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // TODO: HTTPS 환경에서는 true로 변경
        cookie.setPath(COOKIE_PATH);
        cookie.setMaxAge(COOKIE_MAX_AGE);
        response.addCookie(cookie);
    }
    
    /**
     * 게스트 토큰 쿠키 삭제
     * 
     * @param response HTTP 응답
     */
    public void removeGuestToken(HttpServletResponse response) {
        Cookie cookie = new Cookie(GUEST_TOKEN_COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setPath(COOKIE_PATH);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}

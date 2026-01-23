package com.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 임시 JWT 체크 필터
 * 현재는 모든 요청을 통과시킴 (나중에 JWT 검증 로직 추가 예정)
 */
@Slf4j
@Component
public class JWTCheckFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        
        // TODO: 추후 JWT 토큰 검증 로직 추가
        // 현재는 모든 요청을 통과시킴
        
        log.debug("JWTCheckFilter: 요청 통과 - {}", request.getRequestURI());
        
        filterChain.doFilter(request, response);
    }
}

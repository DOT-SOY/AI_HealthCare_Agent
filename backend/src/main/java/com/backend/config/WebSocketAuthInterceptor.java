package com.backend.config;

import com.backend.util.JWTUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * WebSocket STOMP CONNECT 프레임에서 JWT 토큰을 검증하는 인터셉터
 */
@Component
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.info("WebSocket STOMP CONNECT 요청 수신");
            
            // STOMP CONNECT 프레임에서 Authorization 헤더 추출
            List<String> authHeaders = accessor.getNativeHeader("Authorization");
            
            if (authHeaders != null && !authHeaders.isEmpty()) {
                String authHeader = authHeaders.get(0);
                log.info("WebSocket Authorization 헤더 수신: {}", authHeader != null && authHeader.length() > 20 
                    ? authHeader.substring(0, 20) + "..." : authHeader);
                
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    try {
                        String token = authHeader.substring(7);
                        log.info("WebSocket JWT 토큰 검증 시작");
                        Map<String, Object> claims = JWTUtil.validateToken(token);
                        
                        String email = (String) claims.get("email");
                        @SuppressWarnings("unchecked")
                        List<String> roleNames = (List<String>) claims.get("roleNames");
                        
                        // 권한 설정
                        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                        if (roleNames != null) {
                            for (String role : roleNames) {
                                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                            }
                        }
                        
                        // 인증 정보 설정
                        UsernamePasswordAuthenticationToken authentication = 
                            new UsernamePasswordAuthenticationToken(email, null, authorities);
                        accessor.setUser(authentication);
                        
                        log.info("WebSocket 인증 성공: email={}", email);
                    } catch (Exception e) {
                        log.error("WebSocket JWT 검증 실패: {}", e.getMessage(), e);
                        // 인증 실패 시 연결 거부
                        throw new org.springframework.messaging.MessageDeliveryException("WebSocket 인증 실패: " + e.getMessage());
                    }
                } else {
                    log.error("WebSocket CONNECT: Authorization 헤더 형식이 올바르지 않습니다. 헤더: {}", authHeader);
                    throw new org.springframework.messaging.MessageDeliveryException("Authorization 헤더 형식이 올바르지 않습니다.");
                }
            } else {
                log.error("WebSocket CONNECT: Authorization 헤더가 없습니다. 모든 헤더: {}", accessor.toMap());
                throw new org.springframework.messaging.MessageDeliveryException("Authorization 헤더가 없습니다.");
            }
        }
        
        return message;
    }
}


package com.backend.controller;

import com.backend.common.exception.JWTException;
import com.backend.domain.member.Member;
import com.backend.domain.member.MemberRole;
import com.backend.repository.member.MemberRepository;
import com.backend.security.token.RefreshCookieUtil;
import com.backend.security.token.RefreshTokenService;
import com.backend.security.token.TokenType;
import com.backend.util.JWTUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Access Token 재발급 API (Refresh Token 쿠키 기반)
 * - Authorization 헤더가 있으면: 만료되지 않았을 때 동일 토큰 반환, 만료 시 expectedEmail로 binding 검증 후 재발급
 * - Authorization 없으면: Refresh Token만으로 재발급
 */
@Slf4j
@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class APIRefreshController {

    private final RefreshTokenService refreshTokenService;
    private final MemberRepository memberRepository;

    /** Access Token 만료 시간 (초) - 15분 */
    private static final long ACCESS_TOKEN_TTL_SECONDS = 15 * 60L;

    @GetMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request,
            HttpServletResponse response) {

        String refreshTokenRaw = RefreshCookieUtil.get(request);
        if (refreshTokenRaw == null || refreshTokenRaw.isBlank()) {
            return ResponseEntity.status(401).body(Map.of("error", "NULL_REFRESH"));
        }

        // Authorization 헤더 처리 (있으면 사용, 없으면 Refresh Token만으로 진행)
        String accessToken = null;
        String expectedEmail = null;

        if (authHeader != null && authHeader.length() >= 7) {
            accessToken = authHeader.substring(7);
            if (accessToken.isBlank() || "undefined".equals(accessToken)) {
                accessToken = null;
            } else {
                // Access 토큰 타입 확인 (Refresh를 Access처럼 쓰는 우회 방지)
                try {
                    Map<String, Object> accessClaims = JWTUtil.getClaimsAllowExpired(accessToken);
                    Object tokenType = accessClaims.get("tokenType");
                    if (tokenType != null && !TokenType.ACCESS.name().equals(tokenType.toString())) {
                        return ResponseEntity.status(401).body(Map.of("error", "INVALID_TOKEN_TYPE"));
                    }
                } catch (JWTException e) {
                    throw e;
                } catch (Exception e) {
                    accessToken = null;
                }

                // 만료되지 않았으면 그대로 반환
                if (accessToken != null && !JWTUtil.isExpired(accessToken)) {
                    return ResponseEntity.ok(Map.of("accessToken", accessToken));
                }

                // 만료된 Access 토큰에서 email 추출 (binding 검증용)
                if (accessToken != null) {
                    try {
                        Map<String, Object> accessClaims = JWTUtil.getClaimsAllowExpired(accessToken);
                        expectedEmail = (String) accessClaims.get("email");
                    } catch (Exception e) {
                        // 파싱 실패 시 null로 진행 (Refresh Token만으로 진행)
                    }
                }
            }
        }

        // Refresh Token 회전
        String newRefreshToken = refreshTokenService.rotateRefreshToken(refreshTokenRaw, request, expectedEmail);
        RefreshCookieUtil.set(request, response, newRefreshToken, refreshTokenService.refreshCookieMaxAgeSeconds());

        // 새 Access Token claims 구성
        Map<String, Object> refreshClaims = JWTUtil.validateToken(newRefreshToken);
        String email = (String) refreshClaims.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.status(401).body(Map.of("error", "INVALID_REFRESH_CLAIMS"));
        }

        Map<String, Object> newAccessClaims = new HashMap<>();

        // 1순위: 만료된 Access 토큰에서 클레임 복사 (프론트/기존 API 호환)
        if (accessToken != null) {
            try {
                Map<String, Object> oldAccessClaims = JWTUtil.getClaimsAllowExpired(accessToken);
                if (oldAccessClaims != null && !oldAccessClaims.isEmpty()) {
                    if (oldAccessClaims.get("email") != null && !email.equals(oldAccessClaims.get("email"))) {
                        return ResponseEntity.status(401).body(Map.of("error", "REFRESH_BINDING_MISMATCH"));
                    }
                    newAccessClaims.putAll(oldAccessClaims);
                    newAccessClaims.remove("exp");
                    newAccessClaims.remove("iat");
                    newAccessClaims.remove("nbf");
                    newAccessClaims.remove("jti");
                    newAccessClaims.remove("familyId");
                }
            } catch (JWTException e) {
                throw e;
            } catch (Exception e) {
                // MalFormed 등은 무시하고 DB에서 조회
            }
        }

        // 2순위: 클레임이 비어 있으면 DB에서 사용자 정보 조회
        Member member = memberRepository.getWithRoles(email);
        if (member == null || member.isDeleted()) {
            return ResponseEntity.status(401).body(Map.of("error", "MEMBER_NOT_FOUND"));
        }
        List<String> roleNames = member.getRoleList().stream()
                .map(MemberRole::name)
                .collect(Collectors.toList());

        if (newAccessClaims.isEmpty()) {
            newAccessClaims.put("email", member.getEmail());
            newAccessClaims.put("name", member.getName());
            newAccessClaims.put("roleNames", roleNames);
            newAccessClaims.put("amr", refreshClaims.getOrDefault("amr", "pwd").toString());
        } else {
            // 기존 Access claims 복사 경로에서도 memberId/roleNames는 확실히 보장
            newAccessClaims.putIfAbsent("email", member.getEmail());
            newAccessClaims.putIfAbsent("name", member.getName());
            newAccessClaims.putIfAbsent("roleNames", roleNames);
        }

        // 프론트/WS 구독을 위해 항상 memberId 포함
        newAccessClaims.put("memberId", member.getId());

        newAccessClaims.put("tokenType", TokenType.ACCESS.name());
        newAccessClaims.put("auth_time", Instant.now().getEpochSecond());

        String newAccessToken = JWTUtil.generateToken(newAccessClaims, ACCESS_TOKEN_TTL_SECONDS);
        return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
    }
}

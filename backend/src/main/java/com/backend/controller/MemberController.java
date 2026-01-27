package com.backend.controller;

import com.backend.security.token.RefreshCookieUtil;
import com.backend.security.token.RefreshTokenService;
import com.backend.dto.member.MemberDTO;
import com.backend.dto.member.MemberModifyDTO;
import com.backend.service.member.MemberService; // 서비스 인터페이스 임포트
import com.backend.util.JWTUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/join")
    public ResponseEntity<?> join(@Valid @RequestBody MemberDTO memberDTO,
                                  BindingResult bindingResult) {

        // 1. 입력값 검증 실패 시 (DTO 규칙 위반)
        if (bindingResult.hasErrors()) {
            Map<String, String> errorMap = new HashMap<>();

            for (FieldError error : bindingResult.getFieldErrors()) {
                // 예: key="email", value="올바른 이메일 형식이 아닙니다."
                errorMap.put(error.getField(), error.getDefaultMessage());
            }

            // 400 Bad Request와 함께 에러 목록 반환
            return ResponseEntity.badRequest().body(errorMap);
        }

        // 2. 정상 로직 실행
        try {
            memberService.join(memberDTO);
            return ResponseEntity.ok().body(Map.of("message", "회원가입이 완료되었습니다."));
        } catch (IllegalStateException e) {
            // 중복 이메일 등 비즈니스 로직 예외
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            // 서버 내부 에러는 로그로만 남기고, 클라이언트에는 일반 에러 메시지 전달
            log.error("회원가입 중 시스템 오류 발생", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "회원가입 처리 중 오류가 발생했습니다."));
        }
    }

    /**
     * 회원 탈퇴 API (본인만 탈퇴 가능)
     * JWT 인증이 필요하며, 로그인한 본인의 계정만 탈퇴할 수 있습니다.
     */
    @DeleteMapping("/withdraw")
    public ResponseEntity<?> withdraw(Authentication authentication) {
        try {
            // JWTCheckFilter에서 principal을 email 문자열로 저장했으므로
            String email = authentication.getName();

            memberService.withdraw(email);

            return ResponseEntity.ok().body(Map.of("message", "회원 탈퇴가 완료되었습니다."));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("회원 탈퇴 중 시스템 오류 발생", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "회원 탈퇴 처리 중 오류가 발생했습니다."));
        }

    }

    /**
     * 회원 정보 수정 (본인)
     * - JWT 인증 필요
     * - 비밀번호 변경 시 모든 기기 로그아웃을 위해 Refresh Token 패밀리 폐기 + 쿠키 삭제
     */
    @PutMapping("/modify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> modify(@Valid @RequestBody MemberModifyDTO memberModifyDTO,
                                    BindingResult bindingResult,
                                    Authentication authentication,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            Map<String, String> errorMap = new HashMap<>();
            for (FieldError error : bindingResult.getFieldErrors()) {
                errorMap.put(error.getField(), error.getDefaultMessage());
            }
            return ResponseEntity.badRequest().body(errorMap);
        }

        String email = authentication.getName();

        try {
            memberService.modify(email, memberModifyDTO);

            // Refresh Token Family 폐기 + 쿠키 삭제(비밀번호 변경 시 전기기 로그아웃)
            refreshTokenService.revokeAllFamiliesForUser(email);
            RefreshCookieUtil.clear(request, response);

            return ResponseEntity.ok(Map.of("result", "success"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("회원 정보 수정 중 시스템 오류 발생", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "회원 정보 수정 처리 중 오류가 발생했습니다."));
        }
    }

    /**
     * 로그아웃 API (Refresh Token 폐기 + 쿠키 삭제)
     * - Access Token(JWT) 인증 필요
     * - 서버에서 refreshToken 쿠키를 삭제해야 브라우저에서 제거됨(HttpOnly)
     */
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request, HttpServletResponse response) {
        log.info("Logout request received");

        String refreshToken = RefreshCookieUtil.get(request);

        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                Map<String, Object> claims = JWTUtil.validateToken(refreshToken);
                String familyId = (String) claims.get("familyId");

                if (familyId != null && !familyId.isBlank()) {
                    refreshTokenService.revokeFamily(familyId);
                    log.info("Revoked refresh token family: {}", familyId);
                }
            } catch (Exception e) {
                // Refresh Token 파싱/검증 실패해도 쿠키는 삭제해야 함
                log.warn("Failed to revoke refresh token during logout: {}", e.getMessage());
            }
        }

        // Refresh Token 쿠키 삭제 (HttpOnly 쿠키는 서버에서만 삭제 가능)
        RefreshCookieUtil.clear(request, response);
        log.info("Refresh token cookie cleared");

        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");

        // 응답 전송 전에 쿠키 헤더가 적용되도록 flush
        try {
            response.flushBuffer();
        } catch (IOException e) {
            log.warn("Failed to flush response buffer: {}", e.getMessage());
        }

        return ResponseEntity.ok(Map.of("result", "success"));
    }
}
package com.backend.controller;

import com.backend.dto.MemberDTO;
import com.backend.service.MemberService; // 서비스 인터페이스 임포트
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/member") // API 버전 관리를 위해 /api/v1/member 권장하지만 일단 유지
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

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
}
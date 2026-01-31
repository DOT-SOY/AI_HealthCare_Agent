package com.backend.controller.memberinfo;

import com.backend.domain.member.Member;
import com.backend.dto.memberinfo.BodyCompareFeedbackDTO;
import com.backend.dto.memberinfo.MemberInfoBodyDTO;
import com.backend.dto.memberinfo.MemberInfoBodyResponseDTO;
import com.backend.repository.member.MemberRepository;
import com.backend.service.memberinfo.MemberInfoBodyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/member-body-info")
@RequiredArgsConstructor
public class MemberInfoBodyController {

    private final MemberInfoBodyService memberInfoBodyService;
    private final MemberRepository memberRepository;

    /**
     * [생성] OCR 결과 저장 후 직전 1 row와 비교하여 규칙 기반 피드백 반환 (7일 식단/운동 없음)
     */
    @PostMapping("/save-and-compare")
    public ResponseEntity<BodyCompareFeedbackDTO> saveAndCompare(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody MemberInfoBodyDTO requestDto) {
        Long memberId = resolveUserId(email);
        BodyCompareFeedbackDTO feedback = memberInfoBodyService.saveAndCompare(memberId, requestDto);
        return ResponseEntity.ok(feedback);
    }

    /**
     * [생성] 신체 정보 생성
     */
    @PostMapping
    public ResponseEntity<Long> create(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody MemberInfoBodyDTO requestDto) {
        Long memberId = resolveUserId(email);
        Long id = memberInfoBodyService.create(memberId, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    /**
     * [수정] 신체 정보 수정
     */
    @PutMapping("/{id}")
    public ResponseEntity<MemberInfoBodyResponseDTO> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody MemberInfoBodyDTO requestDto) {
        MemberInfoBodyResponseDTO response = memberInfoBodyService.update(id, requestDto);
        return ResponseEntity.ok(response);
    }

    /**
     * [조회] 내 신체 정보 이력 조회 (토큰 기반)
     */
    @GetMapping("/history/me")
    public ResponseEntity<List<MemberInfoBodyResponseDTO>> getMyBodyInfoHistory(
            @AuthenticationPrincipal String email) {
        Long memberId = resolveUserId(email);
        List<MemberInfoBodyResponseDTO> history = memberInfoBodyService.getHistory(memberId);
        return ResponseEntity.ok(history);
    }

    /**
     * [조회] 특정 회원의 신체 정보 이력 조회
     */
    @GetMapping("/history/{memberId}")
    public ResponseEntity<List<MemberInfoBodyResponseDTO>> getBodyInfoHistory(
            @PathVariable("memberId") Long memberId) {
        List<MemberInfoBodyResponseDTO> history = memberInfoBodyService.getHistory(memberId);
        return ResponseEntity.ok(history);
    }

    /**
     * [삭제] 신체 정보 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        memberInfoBodyService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * [인증 헬퍼] 이메일 기반 회원 번호 식별
     */
    private Long resolveUserId(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("인증된 사용자 이메일이 없습니다.");
        }
        return memberRepository.findByEmail(email)
                .filter(m -> !m.isDeleted())
                .map(Member::getId)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));
    }
}


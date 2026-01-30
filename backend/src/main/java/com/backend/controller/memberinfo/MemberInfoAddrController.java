package com.backend.controller.memberinfo;

import com.backend.domain.member.Member;
import com.backend.dto.memberinfo.MemberInfoAddrDTO;
import com.backend.repository.member.MemberRepository;
import com.backend.service.memberinfo.MemberInfoAddrService;
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
@RequestMapping("/api/member-addr-info")
@RequiredArgsConstructor
public class MemberInfoAddrController {

    private final MemberInfoAddrService memberInfoAddrService;
    private final MemberRepository memberRepository;

    /**
     * [조회] 내 배송지 목록 조회 (토큰 기반)
     */
    @GetMapping("/me")
    public ResponseEntity<List<MemberInfoAddrDTO>> getMyList(
            @AuthenticationPrincipal String email) {
        Long memberId = resolveUserId(email);
        List<MemberInfoAddrDTO> list = memberInfoAddrService.getList(memberId);
        return ResponseEntity.ok(list);
    }

    /**
     * [조회] 배송지 목록 조회
     */
    @GetMapping("/member/{memberId}")
    public ResponseEntity<List<MemberInfoAddrDTO>> getList(
            @PathVariable("memberId") Long memberId) {
        List<MemberInfoAddrDTO> list = memberInfoAddrService.getList(memberId);
        return ResponseEntity.ok(list);
    }

    /**
     * [생성] 배송지 생성
     */
    @PostMapping
    public ResponseEntity<Long> create(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody MemberInfoAddrDTO requestDto) {
        Long memberId = resolveUserId(email);
        Long id = memberInfoAddrService.create(memberId, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    /**
     * [수정] 배송지 수정
     */
    @PutMapping("/{id}")
    public ResponseEntity<MemberInfoAddrDTO> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody MemberInfoAddrDTO requestDto) {
        MemberInfoAddrDTO response = memberInfoAddrService.update(id, requestDto);
        return ResponseEntity.ok(response);
    }

    /**
     * [수정] 기본 배송지 설정
     */
    @PutMapping("/{id}/default")
    public ResponseEntity<MemberInfoAddrDTO> setDefault(
            @PathVariable("id") Long id) {
        MemberInfoAddrDTO response = memberInfoAddrService.setDefault(id);
        return ResponseEntity.ok(response);
    }

    /**
     * [삭제] 배송지 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        memberInfoAddrService.delete(id);
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


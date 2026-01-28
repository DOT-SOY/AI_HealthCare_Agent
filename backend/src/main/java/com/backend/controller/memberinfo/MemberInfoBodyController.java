package com.backend.controller.memberinfo;

import com.backend.domain.memberinfo.MemberInfoBody;
import com.backend.dto.memberinfo.MemberInfoBodyDTO;
import com.backend.dto.memberinfo.MemberInfoBodyResponseDTO;
import com.backend.service.memberinfo.MemberInfoBodyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/member-info-body")
@RequiredArgsConstructor
public class MemberInfoBodyController {

    private final MemberInfoBodyService memberInfoBodyService;

    /**
     * 신체 정보 생성 (OCR 입력 포함)
     */
    @PostMapping
    public ResponseEntity<MemberInfoBody> create(@Valid @RequestBody MemberInfoBodyDTO requestDto) {
        MemberInfoBody memberInfoBody = memberInfoBodyService.create(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(memberInfoBody);
    }

    /**
     * 신체 정보 이력 조회 (회원 기준)
     */
    @GetMapping("/history/{memberId}")
    public ResponseEntity<List<MemberInfoBodyResponseDTO>> getBodyInfoHistory(@PathVariable("memberId") Long memberId) {
        List<MemberInfoBodyResponseDTO> history = memberInfoBodyService.getBodyInfoHistory(memberId);
        return ResponseEntity.ok(history);
    }

    /**
     * 신체 정보 수정 (키/몸무게만 수정 가능)
     */
    @PutMapping("/{id}")
    public ResponseEntity<MemberInfoBody> update(@PathVariable("id") Long id,
                                                 @Valid @RequestBody MemberInfoBodyDTO requestDto) {
        MemberInfoBody response = memberInfoBodyService.updateHeightWeight(id, requestDto);
        return ResponseEntity.ok(response);
    }

    /**
     * 신체 정보 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        memberInfoBodyService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 내 신체 정보 이력 조회 (토큰 이메일 기준)
     */
    @GetMapping("/history/me")
    public ResponseEntity<List<MemberInfoBodyResponseDTO>> getMyBodyInfoHistory(Principal principal) {
        String email = principal.getName();
        List<MemberInfoBodyResponseDTO> history = memberInfoBodyService.getBodyInfoHistoryByEmail(email);
        return ResponseEntity.ok(history);
    }
}


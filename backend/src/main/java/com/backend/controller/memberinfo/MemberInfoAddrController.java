package com.backend.controller.memberinfo;

import com.backend.domain.memberinfo.MemberInfoAddr;
import com.backend.dto.memberinfo.MemberInfoAddrDTO;
import com.backend.service.memberinfo.MemberInfoAddrService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/member-info-addr")
@RequiredArgsConstructor
public class MemberInfoAddrController {

    private final MemberInfoAddrService memberInfoAddrService;

    /**
     * 배송지 추가
     */
    @PostMapping
    public ResponseEntity<MemberInfoAddr> create(@Valid @RequestBody MemberInfoAddrDTO requestDto) {
        MemberInfoAddr saved = memberInfoAddrService.create(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * 배송지 목록 조회
     */
    @GetMapping("/member/{memberId}")
    public ResponseEntity<List<MemberInfoAddr>> getList(@PathVariable("memberId") Long memberId) {
        List<MemberInfoAddr> list = memberInfoAddrService.getList(memberId);
        return ResponseEntity.ok(list);
    }

    /**
     * 배송지 수정
     */
    @PutMapping("/{id}")
    public ResponseEntity<MemberInfoAddr> update(@PathVariable("id") Long id,
                                                 @Valid @RequestBody MemberInfoAddrDTO requestDto) {
        MemberInfoAddr updated = memberInfoAddrService.update(id, requestDto);
        return ResponseEntity.ok(updated);
    }

    /**
     * 기본 배송지 설정
     */
    @PutMapping("/{id}/default")
    public ResponseEntity<MemberInfoAddr> setDefault(@PathVariable("id") Long id) {
        MemberInfoAddr updated = memberInfoAddrService.setDefault(id);
        return ResponseEntity.ok(updated);
    }

    /**
     * 배송지 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        memberInfoAddrService.delete(id);
        return ResponseEntity.noContent().build();
    }
}



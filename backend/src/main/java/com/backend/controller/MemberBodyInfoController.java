package com.backend.controller;

import com.backend.dto.MemberBodyInfoRequestDto;
import com.backend.dto.MemberBodyInfoResponseDto;
import com.backend.service.MemberBodyInfoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/member-body-info")
@RequiredArgsConstructor
public class MemberBodyInfoController {
    
    private final MemberBodyInfoService memberBodyInfoService;
    
    /**
     * 신체 정보 생성
     */
    @PostMapping
    public ResponseEntity<MemberBodyInfoResponseDto> create(
            @Valid @RequestBody MemberBodyInfoRequestDto requestDto) {
        MemberBodyInfoResponseDto response = memberBodyInfoService.create(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * 신체 정보 조회 (ID로)
     */
    @GetMapping("/{id}")
    public ResponseEntity<MemberBodyInfoResponseDto> findById(@PathVariable Long id) {
        MemberBodyInfoResponseDto response = memberBodyInfoService.findById(id);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 회원별 신체 정보 조회
     */
    @GetMapping("/member/{memberId}")
    public ResponseEntity<List<MemberBodyInfoResponseDto>> findByMemberId(
            @PathVariable String memberId) {
        List<MemberBodyInfoResponseDto> responses = memberBodyInfoService.findByMemberId(memberId);
        return ResponseEntity.ok(responses);
    }
    
    /**
     * 신체 정보 전체 조회
     */
    @GetMapping
    public ResponseEntity<List<MemberBodyInfoResponseDto>> findAll() {
        List<MemberBodyInfoResponseDto> responses = memberBodyInfoService.findAll();
        return ResponseEntity.ok(responses);
    }
    
    /**
     * 신체 정보 수정
     */
    @PutMapping("/{id}")
    public ResponseEntity<MemberBodyInfoResponseDto> update(
            @PathVariable Long id,
            @Valid @RequestBody MemberBodyInfoRequestDto requestDto) {
        MemberBodyInfoResponseDto response = memberBodyInfoService.update(id, requestDto);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 신체 정보 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        memberBodyInfoService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

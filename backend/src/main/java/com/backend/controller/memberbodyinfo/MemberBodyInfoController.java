package com.backend.controller.memberbodyinfo;

import com.backend.domain.memberbodyinfo.MemberBodyInfo;
import com.backend.dto.memberbodyinfo.MemberBodyInfoDTO;
import com.backend.service.memberbodyinfo.MemberBodyInfoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/member-body-info")
@RequiredArgsConstructor
public class MemberBodyInfoController {
    
    private final MemberBodyInfoService memberBodyInfoService;
    
    /**
     * 신체 정보 생성
     */
    @PostMapping
        public ResponseEntity<MemberBodyInfo> create(
            @Valid @RequestBody MemberBodyInfoDTO requestDto) {
        MemberBodyInfo memberBodyInfo = memberBodyInfoService.create(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(memberBodyInfo);
    }
    
    /**
     * 신체 정보 수정
     */
    @PutMapping("/{id}")
    public ResponseEntity<MemberBodyInfo> update(
            @PathVariable Long id,
            @Valid @RequestBody MemberBodyInfoDTO requestDto) {
        MemberBodyInfo response = memberBodyInfoService.update(id, requestDto);
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

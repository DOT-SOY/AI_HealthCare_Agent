package com.backend.controller.member;

import com.backend.dto.member.MemberProfileResponse;
import com.backend.service.member.MemberProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 프로필 조회 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberProfileController {
    
    private final MemberProfileService memberProfileService;
    
    /**
     * 현재 인증된 회원의 프로필 조회
     * GET /api/members/me/profile
     * 
     * @return MemberProfileResponse
     */
    @GetMapping("/me/profile")
    public ResponseEntity<MemberProfileResponse> getMyProfile() {
        MemberProfileResponse response = memberProfileService.getMyProfile();
        return ResponseEntity.ok(response);
    }
}


package com.backend.service.member;

import com.backend.dto.member.MemberProfileResponse;

/**
 * 사용자 프로필 조회 서비스
 */
public interface MemberProfileService {
    
    /**
     * 현재 인증된 회원의 프로필 조회
     * 추천에 필요한 최소한의 정보만 반환
     * 
     * @return MemberProfileResponse
     */
    MemberProfileResponse getMyProfile();
}


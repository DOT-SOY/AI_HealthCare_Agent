package com.backend.dto.member;

import com.backend.domain.memberinfo.MemberInfoBody.ExercisePurpose;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 사용자 프로필 조회 응답 DTO
 * 추천에 필요한 최소한의 정보만 제공
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberProfileResponse {
    
    // 회원 기본 정보 (주문 생성 시 필요)
    private String name;
    private String email;

    /** 성별 (MALE, FEMALE) - Redis 세션/추천 조건 개인화용 */
    private String gender;

    // 신체 정보
    private Double heightCm;
    private Double weightKg;
    private Double bodyFatPercent;
    private Double bodyWaterPercent;
    
    // 운동 목적
    private ExercisePurpose goal;
    
    // 알러지 및 회피 성분
    private List<String> allergies;
    private List<String> avoid;
}


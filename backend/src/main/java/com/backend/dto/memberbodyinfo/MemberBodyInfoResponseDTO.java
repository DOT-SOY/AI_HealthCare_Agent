package com.backend.dto.memberbodyinfo;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class MemberBodyInfoResponseDTO {
    private Long id;
    private LocalDateTime measuredTime; // 측정 날짜
    private Double weight;              // 체중
    private Double skeletalMuscleMass;  // 골격근량
    private Double bodyFatPercent;      // 체지방률
    private Double bodyWater;           // 체수분
    private Double protein;             // 단백질
    private Double minerals;            // 무기질
    private Double bodyFatMass;         // 체지방량

    // 편의상 날짜를 "yyyy-MM-dd" 문자열로 변환해주는 메서드가 있으면 좋습니다.
}
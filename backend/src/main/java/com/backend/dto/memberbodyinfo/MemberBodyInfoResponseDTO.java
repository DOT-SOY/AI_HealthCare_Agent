package com.backend.dto.memberbodyinfo;

import com.backend.domain.member.Member.Gender; // Gender Enum import
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class MemberBodyInfoResponseDTO {
    private Long id;
    private LocalDateTime measuredTime;

    // ✅ [추가] 회원 기본 정보 (Member 엔티티에서 가져옴)
    private String memberName;
    private Gender gender;
    private LocalDate birthDate;

    // 신체 정보
    private Double height;              // 키 (MemberBodyInfo의 값 사용)
    private Double weight;
    private Double skeletalMuscleMass;
    private Double bodyFatPercent;

    // 상세 분석
    private Double bodyWater;
    private Double protein;
    private Double minerals;
    private Double bodyFatMass;

    // 조절
    private Double targetWeight;
    private Double weightControl;
    private Double fatControl;
    private Double muscleControl;

    //배송정보
    private String shipToName;
    private String shipToPhone;
    private String shipZipcode;
    private String shipAddress1;
    private String shipAddress2;
}
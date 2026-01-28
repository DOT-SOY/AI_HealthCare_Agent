package com.backend.dto.memberinfo;

import com.backend.domain.member.Member;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberInfoBodyResponseDTO {
    private Long id;
    private LocalDateTime measuredTime;

    // 회원 정보
    private Long memberId;
    private String memberName;
    private Member.Gender gender;
    private LocalDate birthDate;

    // 신체 정보
    private Double height;
    private Double weight;
    private Double skeletalMuscleMass;
    private Double bodyFatPercent;
    private Double bodyWater;
    private Double protein;
    private Double minerals;
    private Double bodyFatMass;
    private Double targetWeight;
    private Double weightControl;
    private Double fatControl;
    private Double muscleControl;
    private String purpose;

    // 기본 배송지 정보
    private Long defaultAddrId;
    private String shipToName;
    private String shipToPhone;
    private String shipZipcode;
    private String shipAddress1;
    private String shipAddress2;
}


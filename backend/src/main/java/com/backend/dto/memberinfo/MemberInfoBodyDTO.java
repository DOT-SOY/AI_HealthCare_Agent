package com.backend.dto.memberinfo;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberInfoBodyDTO {
    private Long memberId;
    private LocalDateTime measuredTime;

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
}



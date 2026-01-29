package com.backend.dto.memberinfo;

import com.backend.domain.memberinfo.MemberInfoBody;
import com.backend.domain.memberinfo.MemberInfoBody.ExercisePurpose;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberInfoBodyDTO {

    private Long id;
    private Long memberId;

    // 기본 정보
    private Double height;
    private Double weight;

    // 인바디 상세 정보
    private Double skeletalMuscleMass;
    private Double bodyFatPercent;
    private Double bodyWater;
    private Double protein;
    private Double minerals;
    private Double bodyFatMass;

    // 목표 및 제어값
    private Double targetWeight;
    private Double weightControl;
    private Double fatControl;
    private Double muscleControl;

    // 운동 목적
    private ExercisePurpose exercisePurpose;

    // 측정 시간
    private Instant measuredTime;

    // BaseEntity 필드
    private Instant createdAt;
    private Instant updatedAt;

    public static MemberInfoBodyDTO fromEntity(MemberInfoBody entity) {
        if (entity == null) return null;

        return MemberInfoBodyDTO.builder()
                .id(entity.getId())
                .memberId(entity.getMemberId())
                .height(entity.getHeight())
                .weight(entity.getWeight())
                .skeletalMuscleMass(entity.getSkeletalMuscleMass())
                .bodyFatPercent(entity.getBodyFatPercent())
                .bodyWater(entity.getBodyWater())
                .protein(entity.getProtein())
                .minerals(entity.getMinerals())
                .bodyFatMass(entity.getBodyFatMass())
                .targetWeight(entity.getTargetWeight())
                .weightControl(entity.getWeightControl())
                .fatControl(entity.getFatControl())
                .muscleControl(entity.getMuscleControl())
                .exercisePurpose(entity.getExercisePurpose())
                .measuredTime(entity.getMeasuredTime())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public MemberInfoBody toEntity(Long memberId) {
        return MemberInfoBody.builder()
                .id(this.id)
                .memberId(memberId != null ? memberId : this.memberId)
                .height(this.height)
                .weight(this.weight)
                .skeletalMuscleMass(this.skeletalMuscleMass)
                .bodyFatPercent(this.bodyFatPercent)
                .bodyWater(this.bodyWater)
                .protein(this.protein)
                .minerals(this.minerals)
                .bodyFatMass(this.bodyFatMass)
                .targetWeight(this.targetWeight)
                .weightControl(this.weightControl)
                .fatControl(this.fatControl)
                .muscleControl(this.muscleControl)
                .exercisePurpose(this.exercisePurpose)
                .measuredTime(this.measuredTime != null ? this.measuredTime : Instant.now())
                .build();
    }
}


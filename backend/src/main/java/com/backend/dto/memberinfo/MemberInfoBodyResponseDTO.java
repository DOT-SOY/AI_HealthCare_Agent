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
public class MemberInfoBodyResponseDTO {

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

    // Member 정보 (프론트엔드에서 사용)
    private String memberName;
    private String gender;
    private String birthDate;

    public static MemberInfoBodyResponseDTO fromEntity(MemberInfoBody entity) {
        if (entity == null) return null;

        return MemberInfoBodyResponseDTO.builder()
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

    // Member 정보를 포함한 생성 메서드
    public static MemberInfoBodyResponseDTO fromEntityWithMember(MemberInfoBody entity, com.backend.domain.member.Member member) {
        MemberInfoBodyResponseDTO dto = fromEntity(entity);
        if (dto != null && member != null) {
            dto.setMemberName(member.getName());
            dto.setGender(member.getGender() != null ? member.getGender().name() : null);
            dto.setBirthDate(member.getBirthDate() != null ? member.getBirthDate().toString() : null);
        }
        return dto;
    }
}
 

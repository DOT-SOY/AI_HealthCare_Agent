package com.backend.domain.memberinfo;

import com.backend.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "member_info_body")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MemberInfoBody extends BaseEntity {

    /**
     * 운동 목적 Enum
     * - DIET: 다이어트
     * - MAINTAIN: 유지
     * - BULK_UP: 벌크업
     *
     * MealTarget의 목표 타입과 의미적으로 동일하게 맞춥니다.
     */
    @Getter
    @AllArgsConstructor
    public enum ExercisePurpose {
        DIET("다이어트"),
        MAINTAIN("유지"),
        BULK_UP("벌크업");

        private final String description;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "body_info_id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    // 기본 정보
    @Column(name = "height_cm")
    private Double height;

    @Column(name = "weight_kg")
    private Double weight;

    // 인바디 상세 정보
    @Column(name = "skeletal_muscle_mass")
    private Double skeletalMuscleMass;

    @Column(name = "body_fat_percent")
    private Double bodyFatPercent;

    @Column(name = "body_water")
    private Double bodyWater;

    @Column(name = "protein")
    private Double protein;

    @Column(name = "minerals")
    private Double minerals;

    @Column(name = "body_fat_mass")
    private Double bodyFatMass;

    // 목표 및 제어값
    @Column(name = "target_weight")
    private Double targetWeight;

    @Column(name = "weight_control")
    private Double weightControl;

    @Column(name = "fat_control")
    private Double fatControl;

    @Column(name = "muscle_control")
    private Double muscleControl;

    // 운동 목적 (goal_type 컬럼으로 통일)
    @Enumerated(EnumType.STRING)
    @Column(name = "goal_type", length = 20)
    private ExercisePurpose exercisePurpose;

    // 측정 시간
    @Column(name = "measured_time")
    private java.time.Instant measuredTime;

    // 업데이트 메서드
    public void update(
            Double height, Double weight,
            Double skeletalMuscleMass, Double bodyFatPercent,
            Double bodyWater, Double protein, Double minerals, Double bodyFatMass,
            Double targetWeight, Double weightControl, Double fatControl, Double muscleControl,
            ExercisePurpose exercisePurpose) {
        this.height = height;
        this.weight = weight;
        this.skeletalMuscleMass = skeletalMuscleMass;
        this.bodyFatPercent = bodyFatPercent;
        this.bodyWater = bodyWater;
        this.protein = protein;
        this.minerals = minerals;
        this.bodyFatMass = bodyFatMass;
        this.targetWeight = targetWeight;
        this.weightControl = weightControl;
        this.fatControl = fatControl;
        this.muscleControl = muscleControl;
        this.exercisePurpose = exercisePurpose;
        this.measuredTime = java.time.Instant.now();
    }
}


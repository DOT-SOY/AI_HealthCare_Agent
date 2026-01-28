package com.backend.domain.memberinfo;

import com.backend.domain.member.Member;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "member_info_body")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberInfoBody {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "body_id")
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "measured_time", nullable = false)
    private LocalDateTime measuredTime;

    // --- [기본 신체 정보] ---
    @Column(name = "height")
    private Double height;

    @Column(name = "weight")
    private Double weight;

    @Column(name = "skeletal_muscle_mass")
    private Double skeletalMuscleMass;

    @Column(name = "body_fat_percent")
    private Double bodyFatPercent;

    // --- [체성분 분석 상세] ---
    @Column(name = "body_water")
    private Double bodyWater;

    @Column(name = "protein")
    private Double protein;

    @Column(name = "minerals")
    private Double minerals;

    @Column(name = "body_fat_mass")
    private Double bodyFatMass;

    // --- [체중 조절] ---
    @Column(name = "target_weight")
    private Double targetWeight;

    @Column(name = "weight_control")
    private Double weightControl;

    @Column(name = "fat_control")
    private Double fatControl;

    @Column(name = "muscle_control")
    private Double muscleControl;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose")
    private ExercisePurpose purpose;
}


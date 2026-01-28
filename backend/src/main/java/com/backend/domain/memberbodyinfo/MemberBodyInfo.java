package com.backend.domain.memberbodyinfo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import com.backend.domain.member.Member;
import java.time.LocalDateTime;

@Entity
@Table(name = "member_body_info")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class MemberBodyInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
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

    // --- [배송 정보 (Shipping Info)] ---
    @Column(name = "ship_to_name", length = 50)
    private String shipToName;    // 받는 사람 이름

    @Column(name = "ship_to_phone", length = 20)
    private String shipToPhone;   // 연락처

    @Column(name = "ship_zipcode", length = 10)
    private String shipZipcode;   // 우편번호

    @Column(name = "ship_address1", length = 200)
    private String shipAddress1;  // 기본 주소

    @Column(name = "ship_address2", length = 200)
    private String shipAddress2;  // 상세 주소

    // --- [기타 정보] ---
    @Column(name = "notes", length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose")
    private ExercisePurpose purpose;
}
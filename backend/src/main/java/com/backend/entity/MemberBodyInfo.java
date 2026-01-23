package com.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "member_body_info")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MemberBodyInfo {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;
    
    // 키
    @Column(name = "height")
    private Double height;
    
    // 몸무게
    @Column(name = "weight")
    private Double weight;
    
    // 측정 시간
    @Column(name = "measured_time", nullable = false)
    private LocalDateTime measuredTime;
    
    // 체지방률
    @Column(name = "body_fat_percent")
    private Double bodyFatPercent;
    
    // 골격근량
    @Column(name = "skeletal_muscle_mass")
    private Double skeletalMuscleMass;
    
    // 비고
    @Column(name = "notes", length = 1000)
    private String notes;

    // 목적 (운동 목적)
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose")
    private ExercisePurpose purpose;
}

package com.backend.domain.exercise;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.List;

@Entity
@Table(name = "exercise_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExerciseType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String name; // 운동 이름 (예: "데드리프트", "벤치프레스")
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExerciseCategory mainTarget; // 메인 타겟 (예: BACK, CHEST)
    
    @ElementCollection
    @CollectionTable(name = "exercise_type_sub_targets", joinColumns = @JoinColumn(name = "exercise_type_id"))
    @Column(name = "sub_target")
    @Enumerated(EnumType.STRING)
    @BatchSize(size = 50) // N+1 문제 방지: 배치로 로드
    private List<ExerciseCategory> subTargets; // 서브 타겟 (예: [GLUTE, THIGH])
}


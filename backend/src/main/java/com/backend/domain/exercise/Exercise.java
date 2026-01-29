package com.backend.domain.exercise;

import com.backend.domain.routine.Routine;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "exercise")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Exercise {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_type_id", nullable = true)
    private ExerciseType exerciseType; // 운동 타입 참조 (메인/서브 타겟 포함)
    
    private Integer sets;
    private Integer reps;
    private Double weight; // kg
    
    @Column(name = "order_index")
    private Integer orderIndex;
    
    private boolean completed;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id")
    private Routine routine;
}


package com.backend.domain.routine;

import com.backend.domain.exercise.Exercise;
import com.backend.domain.member.Member;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "routine")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Routine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;
    
    private LocalDate date;
    
    private String title; // 예: "Push Day", "Pull Day"
    
    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary; // AI가 생성한 요약
    
    @Enumerated(EnumType.STRING)
    private RoutineStatus status; // COMPLETED, IN_PROGRESS, EXPECTED
    
    @OneToMany(mappedBy = "routine", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Exercise> exercises = new ArrayList<>();
}

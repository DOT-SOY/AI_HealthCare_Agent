package com.backend.domain.meal;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "daily_target")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MealTarget {

    // =================================================================
    // [Inner Enum Definition]
    // =================================================================
    @Getter
    @AllArgsConstructor
    public enum GoalType {
        DIET("다이어트"),
        MAINTAIN("유지"),
        BULK_UP("벌크업");

        private final String description;
    }
    // =================================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    // [Enum 적용]
    @Enumerated(EnumType.STRING)
    @Column(name = "goal_type", length = 20)
    private GoalType goalType;

    @Column(name = "goal_cal")
    private Integer goalCal;

    @Column(name = "goal_carbs")
    private Integer goalCarbs;

    @Column(name = "goal_protein")
    private Integer goalProtein;

    @Column(name = "goal_fat")
    private Integer goalFat;

   // [추가] AI 심층 상담 내용 저장 (길 수 있으니 TEXT 타입)
    @Column(name = "ai_feedback", columnDefinition = "TEXT")
    private String aiFeedback;

    public void updateFeedback(String aiFeedback) {
        this.aiFeedback = aiFeedback;
    }

    /**
     * 목표 수치 변경
     */
    public void updateTarget(GoalType goalType, Integer goalCal, Integer goalCarbs, Integer goalProtein, Integer goalFat) {
        this.goalType = goalType;
        this.goalCal = goalCal;
        this.goalCarbs = goalCarbs;
        this.goalProtein = goalProtein;
        this.goalFat = goalFat;
    }
}
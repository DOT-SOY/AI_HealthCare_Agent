package com.backend.dto.meal;

import com.backend.domain.meal.MealTarget;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealTargetDto {

    private Long targetId;
    private Long userId;
    private LocalDate targetDate;
    
    // Enum String ("DIET", "MAINTAIN", "BULK_UP")
    private String goalType; 

    private Integer goalCal;
    private Integer goalCarbs;
    private Integer goalProtein;
    private Integer goalFat;
    private String aiFeedback;
    /**
     * DTO -> Entity 변환
     */
    public MealTarget toEntity(Long userId) {
        return MealTarget.builder()
                .targetId(this.targetId)
                .userId(userId)
                .targetDate(this.targetDate)
                .goalType(this.goalType != null ? MealTarget.GoalType.valueOf(this.goalType) : null)
                .goalCal(this.goalCal)
                .goalCarbs(this.goalCarbs)
                .goalProtein(this.goalProtein)
                .goalFat(this.goalFat)
                .build();
    }

    /**
     * Entity -> DTO 변환
     */
    public static MealTargetDto fromEntity(MealTarget target) {
        return MealTargetDto.builder()
                .targetId(target.getTargetId())
                .userId(target.getUserId())
                .targetDate(target.getTargetDate())
                .goalType(target.getGoalType() != null ? target.getGoalType().name() : null)
                .goalCal(target.getGoalCal())
                .goalCarbs(target.getGoalCarbs())
                .goalProtein(target.getGoalProtein())
                .goalFat(target.getGoalFat())
                
                .build();
    }
}
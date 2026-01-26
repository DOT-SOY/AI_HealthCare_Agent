package com.backend.dto;

import com.backend.domain.memberbodyinfo.ExercisePurpose;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MemberBodyInfoRequestDto {
    
    @NotNull(message = "회원 ID는 필수입니다")
    private String memberId;
    
    private Double height;
    
    private Double weight;
    
    @NotNull(message = "측정 시간은 필수입니다")
    private LocalDateTime measuredTime;
    
    private Double bodyFatPercent;
    
    private Double skeletalMuscleMass;
    
    private String notes;
    
    private ExercisePurpose purpose;
}

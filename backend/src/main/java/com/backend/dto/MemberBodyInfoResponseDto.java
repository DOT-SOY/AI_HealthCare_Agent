package com.backend.dto;

import com.backend.domain.memberbodyinfo.ExercisePurpose;
import com.backend.domain.memberbodyinfo.MemberBodyInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberBodyInfoResponseDto {
    
    private Long id;
    private String memberId;
    private String memberName;
    private Double height;
    private Double weight;
    private LocalDateTime measuredTime;
    private Double bodyFatPercent;
    private Double skeletalMuscleMass;
    private String notes;
    private ExercisePurpose purpose;
    
    public static MemberBodyInfoResponseDto from(MemberBodyInfo memberBodyInfo) {
        return MemberBodyInfoResponseDto.builder()
                .id(memberBodyInfo.getId())
                .memberId(String.valueOf(memberBodyInfo.getMember().getId()))
                .memberName(memberBodyInfo.getMember().getName())
                .height(memberBodyInfo.getHeight())
                .weight(memberBodyInfo.getWeight())
                .measuredTime(memberBodyInfo.getMeasuredTime())
                .bodyFatPercent(memberBodyInfo.getBodyFatPercent())
                .skeletalMuscleMass(memberBodyInfo.getSkeletalMuscleMass())
                .notes(memberBodyInfo.getNotes())
                .purpose(memberBodyInfo.getPurpose())
                .build();
    }
}

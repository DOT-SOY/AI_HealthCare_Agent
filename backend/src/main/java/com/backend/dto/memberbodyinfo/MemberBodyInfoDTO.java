package com.backend.dto.memberbodyinfo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MemberBodyInfoDTO {
    private Long id;
    private Long memberId; // 사용자 아이디 (이메일 등)

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime measuredTime;

    // 신체 정보
    private Double height;
    private Double weight;
    private Double skeletalMuscleMass;
    private Double bodyFatPercent;

    // 체성분
    private Double bodyWater;
    private Double protein;
    private Double minerals;
    private Double bodyFatMass;

    // 조절
    private Double targetWeight;
    private Double weightControl;
    private Double fatControl;
    private Double muscleControl;

    // --- [배송 정보 추가] ---
    private String shipToName;
    private String shipToPhone;
    private String shipZipcode;
    private String shipAddress1;
    private String shipAddress2;

    // 기타
    private String notes;
    private String purpose; // Enum 값을 문자열로 전달
}
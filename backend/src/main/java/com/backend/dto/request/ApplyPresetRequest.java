package com.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApplyPresetRequest {
    /** 적용 시작일 (없으면 오늘) */
    private LocalDate startDate;
    /** 0 = 분할 4일 (Push→Pull→Leg→Core+), 1 = 상하체 2일 */
    private Integer presetIndex;
}

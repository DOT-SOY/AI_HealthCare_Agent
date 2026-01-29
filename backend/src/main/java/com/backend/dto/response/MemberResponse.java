package com.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberResponse {
    private Long id;
    private String email;
    private String name;
    private String gender;     // MALE or FEMALE
    private String birthDate;  // YYYY-MM-DD
    private Integer height;    // cm
    private Double weight;     // kg
}



package com.backend.dto.member;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
public class MemberModifyDTO {

    // 새 비밀번호 (수정 시 필수)
    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    @Pattern(regexp = "^(?:(?=.*[A-Za-z])(?=.*\\d)|(?=.*[A-Za-z])(?=.*[$@$!%*#?&])|(?=.*\\d)(?=.*[$@$!%*#?&]))[A-Za-z\\d$@$!%*#?&]{8,20}$",
            message = "비밀번호는 8자 이상, 영문 대소문자/숫자/특수문자 중 2종 이상 포함되어야합니다")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String pw;

    // 이름
    @NotBlank(message = "이름은 필수 입력값입니다.")
    @Pattern(regexp = "^[가-힣a-zA-Z]+$", message = "이름은 한글 또는 영문만 입력 가능합니다.")
    @Size(min = 2, max = 10, message = "이름은 2~10자 사이여야 합니다.")
    private String name;

    // 성별
    @NotBlank(message = "성별을 선택해주세요.")
    @Pattern(regexp = "^(MALE|FEMALE|M|F)$", message = "성별은 정해진 값만 입력 가능합니다.")
    private String gender;

    // 생년월일
    @NotBlank(message = "생년월일을 입력해주세요.")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "생년월일은 YYYY-MM-DD 형식이어야 합니다.")
    private String birthDate;

    // 키(cm)
    @NotNull(message = "키를 입력해주세요.")
    @Min(value = 50, message = "키는 50cm 이상이어야 합니다.")
    @Max(value = 300, message = "키는 300cm 이하여야 합니다.")
    private Integer height;

    // 몸무게(kg)
    @NotNull(message = "몸무게를 입력해주세요.")
    @DecimalMin(value = "1.0", message = "몸무게는 1kg 이상이어야 합니다.")
    private Double weight;
}


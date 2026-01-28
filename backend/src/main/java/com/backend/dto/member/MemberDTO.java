package com.backend.dto.member;

import com.fasterxml.jackson.annotation.JsonProperty; // 추가됨
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberDTO {

    // 1. 이메일 (DB 컬럼 길이에 맞춰 Size 제한 추가 권장)
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 100, message = "이메일은 100자를 초과할 수 없습니다.") // DB 에러 방지
    private String email;

    // 2. 비밀번호
    // 중요: @JsonProperty 설정을 통해 클라이언트로 응답할 때는 비밀번호가 JSON에 포함되지 않도록 차단 (정보 유출 방지)
    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    @Pattern(regexp = "^(?:(?=.*[A-Za-z])(?=.*\\d)|(?=.*[A-Za-z])(?=.*[$@$!%*#?&])|(?=.*\\d)(?=.*[$@$!%*#?&]))[A-Za-z\\d$@$!%*#?&]{8,20}$",
            message = "비밀번호는 8자 이상, 영문 대소문자/숫자/특수문자 중 2종 이상 포함되어야합니다")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String pw;

    // 3. 이름 (화이트리스트 방식 적용됨 - 아주 좋음!)
    @NotBlank(message = "이름은 필수 입력값입니다.")
    @Pattern(regexp = "^[가-힣a-zA-Z]+$", message = "이름은 한글 또는 영문만 입력 가능합니다.")
    @Size(min = 2, max = 10, message = "이름은 2~10자 사이여야 합니다.")
    private String name;

    // 4. 성별 (특정 값만 들어오도록 강제 필요)
    // 예: M, F 또는 MALE, FEMALE 등 정해진 값만 허용해야 함
    @NotBlank(message = "성별을 선택해주세요.")
    @Pattern(regexp = "^(MALE|FEMALE|M|F)$", message = "성별은 정해진 값만 입력 가능합니다.")
    private String gender;

    // 5. 생년월일 (형식 강제)
    // 날짜 형식이 깨지거나 이상한 문자열 공격 방지
    @NotBlank(message = "생년월일을 입력해주세요.")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "생년월일은 YYYY-MM-DD 형식이어야 합니다.")
    private String birthDate;

    // 6. 키(cm)
    @NotNull(message = "키를 입력해주세요.")
    @Min(value = 50, message = "키는 50cm 이상이어야 합니다.")
    @Max(value = 300, message = "키는 300cm 이하여야 합니다.")
    private Integer height;

    // 7. 몸무게(kg)
    // 프론트에서 name을 weigh로 보내는 케이스도 받아주기 위해 alias 허용
    @JsonAlias({"weigh"})
    @NotNull(message = "몸무게를 입력해주세요.")
    @DecimalMin(value = "1.0", message = "몸무게는 1kg 이상이어야 합니다.")
    private Double weight;

}
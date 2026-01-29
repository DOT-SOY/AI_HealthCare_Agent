package com.backend.dto.memberinfo;

import com.backend.domain.memberinfo.MemberInfoAddr;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberInfoAddrDTO {

    private Long id;
    private Long memberId;

    @NotBlank(message = "받는 분 이름은 필수입니다.")
    private String shipToName;

    @NotBlank(message = "연락처는 필수입니다.")
    private String shipToPhone;

    @NotBlank(message = "우편번호는 필수입니다.")
    private String shipZipcode;

    @NotBlank(message = "주소는 필수입니다.")
    private String shipAddress1;

    private String shipAddress2;

    private Boolean isDefault;

    // BaseEntity 필드
    private Instant createdAt;
    private Instant updatedAt;

    public static MemberInfoAddrDTO fromEntity(MemberInfoAddr entity) {
        if (entity == null) return null;

        return MemberInfoAddrDTO.builder()
                .id(entity.getId())
                .memberId(entity.getMemberId())
                .shipToName(entity.getShipToName())
                .shipToPhone(entity.getShipToPhone())
                .shipZipcode(entity.getShipZipcode())
                .shipAddress1(entity.getShipAddress1())
                .shipAddress2(entity.getShipAddress2())
                .isDefault(entity.getIsDefault())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public MemberInfoAddr toEntity(Long memberId) {
        return MemberInfoAddr.builder()
                .id(this.id)
                .memberId(memberId != null ? memberId : this.memberId)
                .shipToName(this.shipToName)
                .shipToPhone(this.shipToPhone)
                .shipZipcode(this.shipZipcode)
                .shipAddress1(this.shipAddress1)
                .shipAddress2(this.shipAddress2)
                .isDefault(this.isDefault != null ? this.isDefault : false)
                .build();
    }
}


package com.backend.domain.memberinfo;

import com.backend.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "member_info_addr")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MemberInfoAddr extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "addr_id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    // 배송지 정보
    @Column(name = "ship_to_name", nullable = false)
    private String shipToName;

    @Column(name = "ship_to_phone", nullable = false)
    private String shipToPhone;

    @Column(name = "ship_zipcode", nullable = false)
    private String shipZipcode;

    @Column(name = "ship_address1", nullable = false)
    private String shipAddress1;

    @Column(name = "ship_address2")
    private String shipAddress2;

    // 기본 배송지 여부
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    // 업데이트 메서드
    public void update(String shipToName, String shipToPhone, String shipZipcode,
                      String shipAddress1, String shipAddress2) {
        this.shipToName = shipToName;
        this.shipToPhone = shipToPhone;
        this.shipZipcode = shipZipcode;
        this.shipAddress1 = shipAddress1;
        this.shipAddress2 = shipAddress2;
    }

    // 기본 배송지 설정
    public void setDefault() {
        this.isDefault = true;
    }

    // 기본 배송지 해제
    public void unsetDefault() {
        this.isDefault = false;
    }
}



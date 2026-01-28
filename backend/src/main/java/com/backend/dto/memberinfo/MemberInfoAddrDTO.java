package com.backend.dto.memberinfo;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberInfoAddrDTO {
    private Long id;
    private Long memberId;

    private String recipientName;
    private String recipientPhone;
    private String zipcode;
    private String address1;
    private String address2;

    private Boolean isDefault;
}



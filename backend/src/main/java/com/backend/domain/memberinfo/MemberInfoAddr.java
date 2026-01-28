package com.backend.domain.memberinfo;

import com.backend.domain.member.Member;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "member_info_addr")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberInfoAddr {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "addr_id")
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "recipient_name", length = 50, nullable = false)
    private String recipientName;

    @Column(name = "recipient_phone", length = 20, nullable = false)
    private String recipientPhone;

    @Column(name = "zipcode", length = 10, nullable = false)
    private String zipcode;

    @Column(name = "address1", length = 200, nullable = false)
    private String address1;

    @Column(name = "address2", length = 200)
    private String address2;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;
}


package com.backend.domain.member;

import com.backend.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "member_addresses",
        indexes = {
                @Index(name = "idx_member_addresses_member_id", columnList = "member_id")
        }
)
public class MemberAddress extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", referencedColumnName = "member_id", nullable = false)
    private Member member;

    @Column(name = "label", length = 50, nullable = false)
    private String label;

    @Column(name = "recipient_name", length = 100, nullable = false)
    private String recipientName;

    @Column(name = "recipient_phone", length = 20, nullable = false)
    private String recipientPhone;

    @Column(name = "zipcode", length = 20, nullable = false)
    private String zipcode;

    @Column(name = "address1", length = 255, nullable = false)
    private String address1;

    @Column(name = "address2", length = 255)
    private String address2;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Builder
    public MemberAddress(Member member,
                         String label,
                         String recipientName,
                         String recipientPhone,
                         String zipcode,
                         String address1,
                         String address2,
                         boolean isDefault) {
        this.member = member;
        this.label = label;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.zipcode = zipcode;
        this.address1 = address1;
        this.address2 = address2;
        this.isDefault = isDefault;
    }
}


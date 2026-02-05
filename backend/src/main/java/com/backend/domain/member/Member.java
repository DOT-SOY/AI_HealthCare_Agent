package com.backend.domain.member;

import com.backend.domain.shop.Product;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "member")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member {

    // 0. 고유 식별자 (PK) - 새로 추가됨
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // DB가 알아서 번호 증가 (1, 2, 3...)
    @Column(name = "member_id")
    private Long id;

    // 1. 이메일 (로그인 ID 역할)
    // PK는 아니지만, 중복된 이메일이 들어오면 안 되므로 unique=true 설정
    @Column(name = "member_email", nullable = false, unique = true)
    private String email;

    // 2. 비밀번호
    @Column(nullable = false)
    private String pw;

    // 3. 이름
    @Column(nullable = false)
    private String name;

    // 4. 성별
    public enum Gender {
        MALE, FEMALE
    }
    @Enumerated(EnumType.STRING) // DB에 "MALE" 글자로 저장
    private Gender gender;       // 실제로 데이터를 담을 그릇

    // 5. 생년월일
    @Column(name = "birth_date")
    private LocalDate birthDate;

    // 6. 탈퇴 여부 (논리 삭제)
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "member_role",
            joinColumns = @JoinColumn(name = "member_id") // Member PK를 FK로 사용
    )
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private List<MemberRole> roleList = new ArrayList<>(); // List로 변경

    // 작성한 상품 목록 (역방향 관계)
    @OneToMany(mappedBy = "createdBy", fetch = FetchType.LAZY)
    @BatchSize(size = 20) // N+1 문제 방지: 20개씩 배치로 조회
    @Builder.Default
    private List<Product> products = new ArrayList<>();

    public void addRole(MemberRole memberRole){
        roleList.add(memberRole);
    }

    public void changePw(String pw) { this.pw = pw; }

    // 탈퇴 처리 메서드 (본인 탈퇴 / 관리자 강제 탈퇴 공통)
    public void changeDeleted(boolean isDeleted) {
        this.isDeleted = isDeleted;
    }
}
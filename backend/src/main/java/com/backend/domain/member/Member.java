package com.backend.domain.member;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString(exclude = "roleList")
public class Member {

    // 고유 식별자 (PK)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // DB가 알아서 번호 증가 (1, 2, 3...)
    @Column(name = "member_id")
    private Long id;

    private String email;

    private String pw;
    private String nickname;
    private boolean social;

    @Builder.Default
    @Column(name = "is_deleted")
    private boolean isDeleted = false; // 삭제 여부

    @Builder.Default
    @Column(name = "is_approved")
    private boolean isApproved = false; // 승인 여부 (소셜 로그인이 아니면 기본 false)


    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "member_role",
            joinColumns = @JoinColumn(name = "member_email")
    )
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private List<MemberRole> roleList = new ArrayList<>(); // List로 변경

    @Column(name = "face_enabled")
    @Builder.Default
    private boolean faceEnabled = false; // 얼굴 로그인 활성화 여부

    public void changeFaceEnabled(boolean faceEnabled) {this.faceEnabled = faceEnabled;}

    public void addRole(MemberRole memberRole){
        roleList.add(memberRole);
    }

    public void changePw(String pw) { this.pw = pw; }
    public void changeNickname(String nickname) { this.nickname = nickname; }
    public void changeSocial(boolean social) { this.social = social; }

    // 관리자 기능용 메서드
    public void changeDeleted(boolean isDeleted) { this.isDeleted = isDeleted; }
    public void changeApproved(boolean isApproved) { this.isApproved = isApproved; }
}
package com.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDate;

@Entity
@Table(name = "member")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member {

    // 1. 아이디
    @Id
    @Column(name = "member_id")
    private String id;

    // 2. 비밀번호
    @Column(nullable = false) // null 허용 X
    private String pw;

    // 3. 이름
    @Column(nullable = false)
    private String name;

    // 4. 성별
    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;

    // 5. 생년월일
    @Column(name = "birth_date")
    private LocalDate birthDate;
}
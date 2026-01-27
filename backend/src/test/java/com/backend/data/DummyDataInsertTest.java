package com.backend.data;

import com.backend.domain.memberbodyinfo.ExercisePurpose;
import com.backend.domain.memberbodyinfo.MemberBodyInfo;
import com.backend.domain.member.Member;
import com.backend.domain.member.Member.Gender;
import com.backend.domain.member.MemberRole;
import com.backend.repository.memberbodyinfo.MemberBodyInfoRepository;
import com.backend.repository.member.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder; // 필수 임포트
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@SpringBootTest
@DisplayName("더미 데이터 삽입 테스트 (user1~9@desk.com)")
class DummyDataInsertTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberBodyInfoRepository memberBodyInfoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder; // ✅ 비밀번호 암호화를 위해 주입

    @Test
    @Transactional
    @Rollback(false) // 테스트 끝나도 DB에 데이터 남김
    @DisplayName("회원(user1~9) 및 신체정보 풀 데이터 삽입")
    void insertFullDummyData() {
        System.out.println("=== 더미 데이터 삽입 시작 ===");

        // 1. 회원 데이터 생성 (user1@desk.com ~ user9@desk.com)
        List<Member> members = createMemberDummyData();
        List<Member> savedMembers = new ArrayList<>();

        for (Member member : members) {
            // 이메일 중복 체크 (이미 있으면 skip 혹은 가져오기)
            if (memberRepository.findByEmail(member.getEmail()).isEmpty()) {
                savedMembers.add(memberRepository.save(member));
                System.out.println("JOIN: " + member.getEmail());
            } else {
                Member exist = memberRepository.findByEmail(member.getEmail()).get();
                savedMembers.add(exist);
                System.out.println("SKIP(Exist): " + member.getEmail());
            }
        }
        memberRepository.flush();

        // 2. 신체 정보 데이터 생성 (각 회원당 3개씩)
        List<MemberBodyInfo> bodyInfos = createFullMemberBodyInfoDummyData(savedMembers);
        List<MemberBodyInfo> newBodyInfos = new ArrayList<>();

        for (MemberBodyInfo bodyInfo : bodyInfos) {
            // 중복 체크 (같은 회원의 같은 측정 시간 데이터 방지)
            List<MemberBodyInfo> existing = memberBodyInfoRepository.findByMemberIdOrderByMeasuredTimeDesc(bodyInfo.getMember().getId());
            boolean isDuplicate = existing.stream()
                    .anyMatch(info -> info.getMeasuredTime().isEqual(bodyInfo.getMeasuredTime()));

            if (!isDuplicate) {
                newBodyInfos.add(bodyInfo);
            }
        }

        if (!newBodyInfos.isEmpty()) {
            memberBodyInfoRepository.saveAll(newBodyInfos);
            memberBodyInfoRepository.flush();
        }

        System.out.println("=== 더미 데이터 삽입 완료 ===");
        System.out.println("총 회원 수: " + savedMembers.size());
        System.out.println("총 신체기록 수: " + newBodyInfos.size());
    }

    /**
     * 회원 더미 데이터 생성 (user1 ~ user9)
     */
    private List<Member> createMemberDummyData() {
        List<Member> members = new ArrayList<>();

        // 이름, 성별 등 매핑 데이터 (9명)
        String[] names = {"홍길동", "김영희", "이철수", "박민수", "최지영", "정수진", "강호동", "유재석", "송지은"};
        Gender[] genders = {Gender.MALE, Gender.FEMALE, Gender.MALE, Gender.MALE, Gender.FEMALE,
                Gender.FEMALE, Gender.MALE, Gender.MALE, Gender.FEMALE};

        // 생년월일 데이터
        LocalDate[] birthDates = {
                LocalDate.of(1990, 1, 15), LocalDate.of(1992, 3, 20), LocalDate.of(1988, 5, 10),
                LocalDate.of(1995, 7, 25), LocalDate.of(1993, 9, 5), LocalDate.of(1991, 11, 12),
                LocalDate.of(1987, 2, 18), LocalDate.of(1989, 4, 30), LocalDate.of(1994, 6, 8)
        };

        // ✅ 비밀번호 "1111" 암호화
        String encodedPassword = passwordEncoder.encode("1111");

        // 1부터 9까지 루프
        for (int i = 0; i < 9; i++) {
            int userNum = i + 1; // user1, user2...
            String email = "user" + userNum + "@desk.com"; // ✅ 요청하신 이메일 포맷

            Member member = Member.builder()
                    .email(email)
                    .pw(encodedPassword) // ✅ 암호화된 비밀번호 사용
                    .name(names[i])
                    .gender(genders[i])
                    .birthDate(birthDates[i])
                    .isDeleted(false)
                    .roleList(new ArrayList<>())
                    .build();

            // 권한 추가
            member.addRole(MemberRole.USER);
            if (i == 0) { // user1은 관리자 권한 추가 부여
                member.addRole(MemberRole.ADMIN);
            }

            members.add(member);
        }

        return members;
    }

    /**
     * 신체 정보 Full 데이터 생성 로직
     */
    private List<MemberBodyInfo> createFullMemberBodyInfoDummyData(List<Member> members) {
        List<MemberBodyInfo> bodyInfos = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.of(2024, 1, 1, 9, 0);
        Random random = new Random();

        for (Member member : members) {
            // 각 회원당 3개의 기록 생성 (1월, 3월, 5월...)
            for (int i = 0; i < 3; i++) {
                LocalDateTime measuredTime = baseTime.plusMonths(i * 2);

                boolean isMale = member.getGender() == Gender.MALE;

                // 1. 기본값 (변동폭을 주어 현실감 있게)
                double height = (isMale ? 175.0 : 163.0) + (random.nextDouble() * 4 - 2);
                // 회차별 체중 변화 (조금씩 감량하는 시나리오)
                double weightBase = (isMale ? 80.0 : 60.0);
                double weight = weightBase - (i * 1.5) + (random.nextDouble() * 2 - 1);

                // 2. 체성분 계산
                double fatPercent = (isMale ? 20.0 : 28.0) - (i * 0.5); // 체지방률 조금씩 감소
                double fatMass = weight * (fatPercent / 100.0);
                double muscleMass = weight * (isMale ? 0.45 : 0.36) + (i * 0.2); // 근육량 조금씩 증가

                double water = weight * 0.55;
                double protein = weight * 0.17;
                double minerals = weight * 0.055;

                // 3. 목표치
                double targetWeight = weightBase - 5.0;
                double weightControl = targetWeight - weight;

                MemberBodyInfo info = MemberBodyInfo.builder()
                        .member(member)
                        .measuredTime(measuredTime)
                        // 기본
                        .height(round(height))
                        .weight(round(weight))
                        .skeletalMuscleMass(round(muscleMass))
                        .bodyFatPercent(round(fatPercent))
                        // 상세
                        .bodyFatMass(round(fatMass))
                        .bodyWater(round(water))
                        .protein(round(protein))
                        .minerals(round(minerals))
                        // 조절
                        .targetWeight(round(targetWeight))
                        .weightControl(round(weightControl))
                        .fatControl(round(-2.0))
                        .muscleControl(round(1.5))
                        // 배송 및 기타 (Frontend 요구사항 반영)
                        .shipToName(member.getName())
                        .shipToPhone("010-1234-" + (5000 + members.indexOf(member)))
                        .shipZipcode("062" + i + "3")
                        .shipAddress1("서울시 강남구 테헤란로 " + (members.indexOf(member) + 1) + "길")
                        .shipAddress2((i + 1) + "01호")
                        .purpose(ExercisePurpose.values()[random.nextInt(ExercisePurpose.values().length)])
                        .notes(member.getName() + "님 " + (i + 1) + "회차 측정. 상태 양호.")
                        .build();

                bodyInfos.add(info);
            }
        }
        return bodyInfos;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
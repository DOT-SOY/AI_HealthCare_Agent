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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@SpringBootTest
@DisplayName("그래프용 추세 데이터 삽입 (1년치 변화)")
class DummyDataInsertTest {

    @Autowired private MemberRepository memberRepository;
    @Autowired private MemberBodyInfoRepository memberBodyInfoRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    @Transactional
    @Rollback(false)
    @DisplayName("회원(user1~9) 및 12개월치 신체 변화 데이터 삽입")
    void insertTrendDummyData() {
        // 1. 회원 생성
        List<Member> members = createMemberDummyData();
        List<Member> savedMembers = new ArrayList<>();

        for (Member member : members) {
            if (memberRepository.findByEmail(member.getEmail()).isEmpty()) {
                savedMembers.add(memberRepository.save(member));
            } else {
                savedMembers.add(memberRepository.findByEmail(member.getEmail()).get());
            }
        }
        memberRepository.flush();

        // 2. 신체 데이터 생성 (추세 반영)
        List<MemberBodyInfo> bodyInfos = createTrendBodyInfoData(savedMembers);

        // 기존 데이터와 중복 방지 (날짜 기준)
        List<MemberBodyInfo> finalInfos = new ArrayList<>();
        for (MemberBodyInfo info : bodyInfos) {
            boolean exists = memberBodyInfoRepository.findByMemberIdOrderByMeasuredTimeDesc(info.getMember().getId())
                    .stream().anyMatch(e -> e.getMeasuredTime().isEqual(info.getMeasuredTime()));
            if (!exists) finalInfos.add(info);
        }

        if (!finalInfos.isEmpty()) {
            memberBodyInfoRepository.saveAll(finalInfos);
        }

        System.out.println("=== 데이터 삽입 완료 ===");
        System.out.println("생성된 신체 기록 수: " + finalInfos.size());
    }

    private List<Member> createMemberDummyData() {
        // ... (기존 회원 생성 로직과 동일, 생략 가능하지만 전체 코드 위해 유지)
        List<Member> members = new ArrayList<>();
        String[] names = {"홍길동", "김영희", "이철수", "박민수", "최지영", "정수진", "강호동", "유재석", "송지은"};
        Gender[] genders = {Gender.MALE, Gender.FEMALE, Gender.MALE, Gender.MALE, Gender.FEMALE, Gender.FEMALE, Gender.MALE, Gender.MALE, Gender.FEMALE};
        String pw = passwordEncoder.encode("1111");

        for (int i = 0; i < 9; i++) {
            Member m = Member.builder()
                    .email("user" + (i + 1) + "@desk.com")
                    .pw(pw)
                    .name(names[i])
                    .gender(genders[i])
                    .birthDate(LocalDate.of(1990 + i, 1, 1))
                    .build();
            m.addRole(MemberRole.USER);
            if (i == 0) m.addRole(MemberRole.ADMIN);
            members.add(m);
        }
        return members;
    }

    // 🔥 [핵심] 추세가 있는 데이터 생성 로직
    private List<MemberBodyInfo> createTrendBodyInfoData(List<Member> members) {
        List<MemberBodyInfo> list = new ArrayList<>();
        // 2023년 1월부터 시작
        LocalDateTime startDate = LocalDateTime.of(2023, 1, 1, 9, 0, 0);
        Random random = new Random();

        for (Member member : members) {
            boolean isMale = member.getGender() == Gender.MALE;

            // 초기값 설정 (시작 시점)
            double currentHeight = isMale ? 175.0 : 162.0;
            double currentWeight = isMale ? 85.0 : 65.0; // 다이어트 전
            double currentMuscle = isMale ? 32.0 : 22.0; // 근육량
            double targetWeight = currentWeight - 10.0;  // 목표: -10kg 감량

            // 12개월치 데이터 생성 (매월 변화)
            for (int i = 0; i < 12; i++) {
                LocalDateTime date = startDate.plusMonths(i);

                // [변화 로직]
                // 1. 몸무게: 매달 0.5 ~ 0.8kg 감량 (가끔 정체기)
                double weightLoss = (random.nextDouble() * 0.5) + 0.3;
                if (i % 4 == 0) weightLoss = -0.2; // 4개월마다 요요 살짝 옴
                currentWeight -= weightLoss;

                // 2. 근육량: 매달 0.1 ~ 0.2kg 증가 (운동 효과)
                double muscleGain = (random.nextDouble() * 0.2);
                currentMuscle += muscleGain;

                // 3. 체지방률 계산 (몸무게에서 근육, 뼈 등 제외하고 역산)
                // 체지방량 = 체중 - (근육량 + 제지방기타)
                // 단순화: 체지방률 = (체중 - 근육량 * 1.8) / 체중 * 100 (대략적 공식 활용)
                double fatRate = ((currentWeight - (currentMuscle * 1.5)) / currentWeight) * 100;
                if (fatRate < 5) fatRate = 5.0; // 최소치 방어

                // 상세 데이터 유도 계산
                double bodyWater = currentWeight * 0.55; // 체수분
                double protein = currentWeight * 0.18;   // 단백질
                double minerals = currentWeight * 0.05;  // 무기질
                double bodyFatMass = currentWeight * (fatRate / 100.0); // 체지방량(kg)

                // 조절 가이드
                double weightControl = targetWeight - currentWeight;
                double muscleControl = (isMale ? 38.0 : 26.0) - currentMuscle; // 목표 근육량 대비

                MemberBodyInfo info = MemberBodyInfo.builder()
                        .member(member)
                        .measuredTime(date)

                        // 그래프용 핵심 데이터
                        .height(round(currentHeight))
                        .weight(round(currentWeight))
                        .skeletalMuscleMass(round(currentMuscle))
                        .bodyFatPercent(round(fatRate))

                        // 상세 분석 데이터
                        .bodyWater(round(bodyWater))
                        .protein(round(protein))
                        .minerals(round(minerals))
                        .bodyFatMass(round(bodyFatMass))

                        // 조절 가이드
                        .targetWeight(round(targetWeight))
                        .weightControl(round(weightControl))
                        .fatControl(round(weightControl * 0.8)) // 감량의 80%는 지방으로
                        .muscleControl(round(muscleControl))

                        // 배송 정보 (빈 값 채우기)
                        .shipToName(member.getName())
                        .shipToPhone("010-1234-5678")
                        .shipAddress1("서울시 강남구")
                        .shipZipcode("12345")

                        .purpose(ExercisePurpose.DIET)
                        .notes(i + 1 + "개월차 측정. 꾸준히 변화 중.")
                        .build();

                list.add(info);
            }
        }
        return list;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
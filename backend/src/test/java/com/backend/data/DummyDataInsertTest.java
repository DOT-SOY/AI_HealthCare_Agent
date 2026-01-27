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
import org.springframework.test.annotation.Rollback;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


@SpringBootTest
@DisplayName("더미 데이터 삽입 테스트 (풀 데이터)")
class DummyDataInsertTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberBodyInfoRepository memberBodyInfoRepository;

    @Test
    @Rollback(false) // DB에 커밋
    @DisplayName("전체 더미 데이터 삽입 (회원 + 신체정보 모든 필드 포함)")
    void insertFullDummyData() {
        System.out.println("=== 전체 더미 데이터 삽입 시작 ===");

        // 1. 회원 데이터 생성 및 저장
        List<Member> members = createMemberDummyData();
        List<Member> savedMembers = new ArrayList<>();

        for (Member member : members) {
            // 이메일 중복 체크
            if (memberRepository.findByEmail(member.getEmail()).isEmpty()) {
                savedMembers.add(memberRepository.save(member));
            } else {
                // 이미 존재하면 DB에서 가져옴
                savedMembers.add(memberRepository.findByEmail(member.getEmail()).get());
            }
        }
        memberRepository.flush();
        System.out.println(String.format(">>> 회원 데이터 준비 완료: %d명", savedMembers.size()));

        // 2. 신체 정보 데이터 생성 및 저장 (모든 필드 포함)
        List<MemberBodyInfo> bodyInfos = createFullMemberBodyInfoDummyData(savedMembers);
        List<MemberBodyInfo> newBodyInfos = new ArrayList<>();

        for (MemberBodyInfo bodyInfo : bodyInfos) {
            // 중복 체크 (회원 + 측정시간)
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

        System.out.println(String.format(">>> 신체 정보 데이터 삽입 완료: %d개", newBodyInfos.size()));
        System.out.println("=== 전체 더미 데이터 삽입 종료 ===");
    }

    /**
     * 회원 더미 데이터 생성 (권한 포함)
     */
    private List<Member> createMemberDummyData() {
        List<Member> members = new ArrayList<>();
        
        String[] names = {"홍길동", "김영희", "이철수", "박민수", "최지영", 
                         "정수진", "강호동", "유재석", "송지은", "한소희"};
        Gender[] genders = {Gender.MALE, Gender.FEMALE, Gender.MALE, Gender.MALE, Gender.FEMALE,
                           Gender.FEMALE, Gender.MALE, Gender.MALE, Gender.FEMALE, Gender.FEMALE};
        LocalDate[] birthDates = {
            LocalDate.of(1990, 1, 15), LocalDate.of(1992, 3, 20), LocalDate.of(1988, 5, 10),
            LocalDate.of(1995, 7, 25), LocalDate.of(1993, 9, 5), LocalDate.of(1991, 11, 12),
            LocalDate.of(1987, 2, 18), LocalDate.of(1989, 4, 30), LocalDate.of(1994, 6, 8),
            LocalDate.of(1996, 8, 22)
        };

        for (int i = 0; i < 10; i++) {
            Member member = Member.builder()
                    .email("user" + String.format("%03d", i + 1) + "@example.com")
                    .pw("pass" + (i + 1)) // 실제 서비스에선 암호화 필요
                    .name(names[i])
                    .gender(genders[i])
                    .birthDate(birthDates[i])
                    .isDeleted(false)
                    .roleList(new ArrayList<>()) // 리스트 초기화
                    .build();
            
            // 권한 추가
            member.addRole(MemberRole.USER);
            if (i == 0) member.addRole(MemberRole.ADMIN); // 첫 번째 회원은 관리자 권한도 부여

            members.add(member);
        }

        return members;
    }

    /**
     * 신체 정보 Full 데이터 생성
     */
    private List<MemberBodyInfo> createFullMemberBodyInfoDummyData(List<Member> members) {
        List<MemberBodyInfo> bodyInfos = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.of(2024, 1, 1, 9, 0); // 시작 시간
        Random random = new Random();

        for (Member member : members) {
            // 각 회원당 3개의 기록 생성 (2달 간격)
            for (int i = 0; i < 3; i++) {
                LocalDateTime measuredTime = baseTime.plusMonths(i * 2);
                
                // 1. 기본 설정 (성별에 따른 평균값)
                boolean isMale = member.getGender() == Gender.MALE;
                double height = (isMale ? 175.0 : 163.0) + (random.nextDouble() * 10 - 5);
                double weight = (isMale ? 75.0 : 55.0) + (random.nextDouble() * 10 - 5);
                
                // 2. 체성분 계산 (대략적인 비율로 계산하여 현실감 부여)
                // 체지방률: 남성 15~20%, 여성 20~28%
                double fatPercent = (isMale ? 18.0 : 25.0) + (random.nextDouble() * 6 - 3);
                double fatMass = weight * (fatPercent / 100.0);
                
                // 골격근량: 체중 - 체지방 - (뼈+기타) 대략 계산
                double muscleMass = weight * (isMale ? 0.45 : 0.36) + (random.nextDouble() * 2);
                
                // 체수분: 체중의 약 50~60%
                double water = weight * 0.55 + (random.nextDouble() * 2 - 1);
                
                // 단백질: 체중의 약 15~20%
                double protein = weight * 0.17 + (random.nextDouble() * 1);
                
                // 무기질: 체중의 약 5~6%
                double minerals = weight * 0.055 + (random.nextDouble() * 0.5);

                // 3. 목표 및 조절 가이드
                double targetWeight = weight - 3.0; // 현재보다 3kg 감량 목표
                double weightControl = targetWeight - weight;
                double fatControl = -2.5; // 지방 2.5kg 감량 권장
                double muscleControl = 1.5; // 근육 1.5kg 증량 권장

                MemberBodyInfo info = MemberBodyInfo.builder()
                        .member(member)
                        .measuredTime(measuredTime)
                        
                        // [기본 정보]
                        .height(round(height))
                        .weight(round(weight))
                        .skeletalMuscleMass(round(muscleMass))
                        .bodyFatPercent(round(fatPercent))
                        
                        // [상세 정보]
                        .bodyFatMass(round(fatMass))
                        .bodyWater(round(water))
                        .protein(round(protein))
                        .minerals(round(minerals))
                        
                        // [조절 가이드]
                        .targetWeight(round(targetWeight))
                        .weightControl(round(weightControl))
                        .fatControl(fatControl)
                        .muscleControl(muscleControl)
                        
                        // [배송 정보]
                        .shipToName(member.getName())
                        .shipToPhone("010-" + (1000 + i) + "-" + (5678 + i))
                        .shipZipcode("062" + i + "4")
                        .shipAddress1("서울시 강남구 테헤란로 " + (100 + i) + "길")
                        .shipAddress2((i + 1) + "0" + (i + 1) + "호")
                        
                        // [기타]
                        .purpose(ExercisePurpose.values()[i % ExercisePurpose.values().length])
                        .notes(i + 1 + "회차 정기 측정 결과입니다. 식단 조절 필요.")
                        .build();

                bodyInfos.add(info);
            }
        }
        return bodyInfos;
    }

    // 소수점 1자리 반올림 유틸 메서드
    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
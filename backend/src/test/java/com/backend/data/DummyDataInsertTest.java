package com.backend.data;

import com.backend.entity.Gender;
import com.backend.entity.Member;
import com.backend.entity.MemberBodyInfo;
import com.backend.repository.MemberBodyInfoRepository;
import com.backend.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("더미 데이터 삽입 테스트 (실제 DB에 저장)")
class DummyDataInsertTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberBodyInfoRepository memberBodyInfoRepository;

    @Test
    @Rollback(false)
    @DisplayName("회원 더미 데이터 삽입 테스트 (실제 DB에 저장)")
    void testInsertMemberDummyData() {
        // 기존 데이터 확인
        long existingCount = memberRepository.count();
        System.out.println(String.format("기존 회원 수: %d", existingCount));
        
        // given
        List<Member> members = createMemberDummyData();
        
        // 중복 체크: 이미 존재하는 ID는 제외
        List<Member> newMembers = new ArrayList<>();
        for (Member member : members) {
            if (!memberRepository.existsById(member.getId())) {
                newMembers.add(member);
            } else {
                System.out.println(String.format("회원 ID %s는 이미 존재합니다. 건너뜁니다.", member.getId()));
            }
        }
        
        if (newMembers.isEmpty()) {
            System.out.println("삽입할 새로운 회원 데이터가 없습니다.");
            return;
        }

        // when
        List<Member> savedMembers = memberRepository.saveAll(newMembers);
        memberRepository.flush();

        // then
        assertThat(savedMembers).hasSize(newMembers.size());
        System.out.println(String.format("=== 회원 더미 데이터 삽입 완료: %d명 ===", savedMembers.size()));
        savedMembers.forEach(member -> 
            System.out.println(String.format("ID: %s, 이름: %s, 성별: %s, 생년월일: %s", 
                member.getId(), member.getName(), member.getGender(), member.getBirthDate()))
        );
        System.out.println(String.format("총 회원 수: %d", memberRepository.count()));
    }

    @Test
    @Rollback(false)
    @DisplayName("신체 정보 더미 데이터 삽입 테스트 (실제 DB에 저장)")
    void testInsertMemberBodyInfoDummyData() {
        // given - 기존 회원 조회 또는 생성
        List<Member> existingMembers = memberRepository.findAll();
        
        if (existingMembers.isEmpty()) {
            // 회원이 없으면 먼저 생성
            System.out.println("회원 데이터가 없습니다. 회원 데이터를 먼저 생성합니다.");
            List<Member> members = createMemberDummyData();
            existingMembers = memberRepository.saveAll(members);
            memberRepository.flush();
        }
        
        // 기존 신체 정보 개수 확인
        long existingBodyInfoCount = memberBodyInfoRepository.count();
        System.out.println(String.format("기존 신체 정보 수: %d", existingBodyInfoCount));

        // when - 각 회원에 대한 신체 정보 생성
        List<MemberBodyInfo> bodyInfos = createMemberBodyInfoDummyData(existingMembers);
        List<MemberBodyInfo> savedBodyInfos = memberBodyInfoRepository.saveAll(bodyInfos);
        memberBodyInfoRepository.flush();

        // then
        assertThat(savedBodyInfos).hasSize(bodyInfos.size());
        System.out.println("=== 신체 정보 더미 데이터 삽입 완료 ===");
        System.out.println(String.format("삽입된 신체 정보: %d개", savedBodyInfos.size()));
        System.out.println(String.format("총 신체 정보 수: %d", memberBodyInfoRepository.count()));
    }

    @Test
    @Rollback(false)
    @DisplayName("전체 더미 데이터 삽입 테스트 (실제 DB에 저장)")
    void testInsertAllDummyData() {
        System.out.println("=== 전체 더미 데이터 삽입 시작 ===");
        
        // 기존 데이터 확인
        long existingMemberCount = memberRepository.count();
        long existingBodyInfoCount = memberBodyInfoRepository.count();
        System.out.println(String.format("기존 회원 수: %d, 기존 신체 정보 수: %d", 
            existingMemberCount, existingBodyInfoCount));
        
        // given
        List<Member> members = createMemberDummyData();
        
        // 중복 체크: 이미 존재하는 ID는 제외
        List<Member> newMembers = new ArrayList<>();
        for (Member member : members) {
            if (!memberRepository.existsById(member.getId())) {
                newMembers.add(member);
            } else {
                System.out.println(String.format("회원 ID %s는 이미 존재합니다. 건너뜁니다.", member.getId()));
            }
        }
        
        List<Member> savedMembers;
        if (newMembers.isEmpty()) {
            System.out.println("새로운 회원 데이터가 없습니다. 기존 회원을 사용합니다.");
            savedMembers = memberRepository.findAll();
        } else {
            // when - 회원 데이터 삽입
            savedMembers = memberRepository.saveAll(newMembers);
            memberRepository.flush();
            System.out.println(String.format("새로운 회원 %d명 삽입 완료", savedMembers.size()));
        }
        
        // 신체 정보 데이터 삽입
        List<MemberBodyInfo> bodyInfos = createMemberBodyInfoDummyData(savedMembers);
        List<MemberBodyInfo> savedBodyInfos = memberBodyInfoRepository.saveAll(bodyInfos);
        memberBodyInfoRepository.flush();

        // then
        System.out.println("=== 전체 더미 데이터 삽입 완료 ===");
        System.out.println(String.format("총 회원: %d명", memberRepository.count()));
        System.out.println(String.format("총 신체 정보: %d개", memberBodyInfoRepository.count()));
        System.out.println(String.format("이번에 삽입된 신체 정보: %d개", savedBodyInfos.size()));
        
        // 각 회원별 신체 정보 개수 확인
        savedMembers.forEach(member -> {
            List<MemberBodyInfo> memberBodyInfos = memberBodyInfoRepository.findByMemberId(member.getId());
            System.out.println(String.format("회원 %s(%s): 신체 정보 %d개", 
                member.getName(), member.getId(), memberBodyInfos.size()));
        });
    }

    /**
     * 회원 더미 데이터 생성
     */
    private List<Member> createMemberDummyData() {
        List<Member> members = new ArrayList<>();
        
        String[] names = {"홍길동", "김영희", "이철수", "박민수", "최지영", 
                         "정수진", "강호동", "유재석", "송지은", "한소희"};
        Gender[] genders = {Gender.MALE, Gender.FEMALE, Gender.MALE, Gender.MALE, Gender.FEMALE,
                           Gender.FEMALE, Gender.MALE, Gender.MALE, Gender.FEMALE, Gender.FEMALE};
        LocalDate[] birthDates = {
            LocalDate.of(1990, 1, 15),
            LocalDate.of(1992, 3, 20),
            LocalDate.of(1988, 5, 10),
            LocalDate.of(1995, 7, 25),
            LocalDate.of(1993, 9, 5),
            LocalDate.of(1991, 11, 12),
            LocalDate.of(1987, 2, 18),
            LocalDate.of(1989, 4, 30),
            LocalDate.of(1994, 6, 8),
            LocalDate.of(1996, 8, 22)
        };

        for (int i = 0; i < 10; i++) {
            Member member = Member.builder()
                    .id("user" + String.format("%03d", i + 1))
                    .pw("password" + (i + 1))
                    .name(names[i])
                    .gender(genders[i])
                    .birthDate(birthDates[i])
                    .build();
            members.add(member);
        }

        return members;
    }

    /**
     * 신체 정보 더미 데이터 생성
     */
    private List<MemberBodyInfo> createMemberBodyInfoDummyData(List<Member> members) {
        List<MemberBodyInfo> bodyInfos = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.of(2024, 1, 1, 10, 0);

        for (Member member : members) {
            // 각 회원당 3개의 신체 정보 생성 (과거, 현재, 미래)
            for (int i = 0; i < 3; i++) {
                LocalDateTime measuredTime = baseTime.plusMonths(i * 2);
                
                // 성별에 따라 다른 기본값 설정
                double baseHeight = member.getGender() == Gender.MALE ? 175.0 : 165.0;
                double baseWeight = member.getGender() == Gender.MALE ? 75.0 : 55.0;
                double baseBodyFat = member.getGender() == Gender.MALE ? 15.0 : 22.0;
                double baseMuscle = member.getGender() == Gender.MALE ? 55.0 : 35.0;

                // 약간의 변동 추가
                double height = baseHeight + (Math.random() * 5 - 2.5); // ±2.5cm
                double weight = baseWeight + (Math.random() * 5 - 2.5); // ±2.5kg
                double bodyFat = baseBodyFat + (Math.random() * 3 - 1.5); // ±1.5%
                double muscle = baseMuscle + (Math.random() * 3 - 1.5); // ±1.5kg

                MemberBodyInfo bodyInfo = new MemberBodyInfo();
                bodyInfo.setMember(member);
                bodyInfo.setHeight(Math.round(height * 10.0) / 10.0); // 소수점 1자리
                bodyInfo.setWeight(Math.round(weight * 10.0) / 10.0);
                bodyInfo.setMeasuredTime(measuredTime);
                bodyInfo.setBodyFatPercent(Math.round(bodyFat * 10.0) / 10.0);
                bodyInfo.setSkeletalMuscleMass(Math.round(muscle * 10.0) / 10.0);
                bodyInfo.setNotes(String.format("%s의 %d번째 측정 기록", member.getName(), i + 1));

                bodyInfos.add(bodyInfo);
            }
        }

        return bodyInfos;
    }
}

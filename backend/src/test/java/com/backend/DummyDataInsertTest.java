package com.backend;

import com.backend.domain.member.Member;
import com.backend.domain.member.Member.Gender;
import com.backend.domain.memberinfo.MemberInfoAddr;
import com.backend.domain.memberinfo.MemberInfoBody;
import com.backend.domain.memberinfo.MemberInfoBody.ExercisePurpose;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.memberinfo.MemberInfoAddrRepository;
import com.backend.repository.memberinfo.MemberInfoBodyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.Rollback;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Member, MemberInfoAddr, MemberInfoBody 도메인에 대한 더미 데이터 삽입 테스트.
 * DB에 실제로 insert 합니다. (@Rollback(false))
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("더미 데이터 삽입 테스트 (Member / MemberInfoAddr / MemberInfoBody)")
class DummyDataInsertTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberInfoAddrRepository memberInfoAddrRepository;

    @Autowired
    private MemberInfoBodyRepository memberInfoBodyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @Rollback(false)
    @DisplayName("회원 + 배송지 + 신체정보 더미 데이터 일괄 삽입")
    void insertAllDummyData() {
        // 1) 회원 더미 데이터 생성 및 저장
        List<Member> members = createMemberDummyData();
        List<Member> newMembers = new ArrayList<>();
        for (Member m : members) {
            if (memberRepository.findByEmail(m.getEmail()).isEmpty()) {
                newMembers.add(m);
            }
        }
        if (newMembers.isEmpty()) {
            System.out.println("삽입할 새 회원이 없습니다. 기존 회원 사용.");
        } else {
            List<Member> saved = memberRepository.saveAll(newMembers);
            memberRepository.flush();
            System.out.println("회원 " + saved.size() + "명 삽입 완료.");
        }

        List<Member> allMembers = memberRepository.findAll();
        if (allMembers.isEmpty()) {
            System.out.println("회원이 없어 배송지/신체정보 삽입을 건너뜁니다.");
            return;
        }

        // 2) 배송지 더미 데이터 (회원당 1~2개)
        List<MemberInfoAddr> addrs = createMemberInfoAddrDummyData(allMembers);
        List<MemberInfoAddr> savedAddrs = memberInfoAddrRepository.saveAll(addrs);
        memberInfoAddrRepository.flush();
        System.out.println("배송지 " + savedAddrs.size() + "건 삽입 완료.");

        // 3) 신체정보 더미 데이터 (회원당 2~3건)
        List<MemberInfoBody> bodies = createMemberInfoBodyDummyData(allMembers);
        List<MemberInfoBody> savedBodies = memberInfoBodyRepository.saveAll(bodies);
        memberInfoBodyRepository.flush();
        System.out.println("신체정보 " + savedBodies.size() + "건 삽입 완료.");

        assertThat(memberRepository.count()).isPositive();
        assertThat(savedAddrs).hasSize(addrs.size());
        assertThat(savedBodies).hasSize(bodies.size());
    }

    private List<Member> createMemberDummyData() {
        List<Member> list = new ArrayList<>();
        String[] names = {"홍길동", "김영희", "이철수", "박민수", "최지영", "정수진", "강호동", "유재석", "송지은", "한소희"};
        Gender[] genders = {Gender.MALE, Gender.FEMALE, Gender.MALE, Gender.MALE, Gender.FEMALE,
                Gender.FEMALE, Gender.MALE, Gender.MALE, Gender.FEMALE, Gender.FEMALE};
        LocalDate[] birthDates = {
                LocalDate.of(1990, 1, 15), LocalDate.of(1992, 3, 20), LocalDate.of(1988, 5, 10),
                LocalDate.of(1995, 7, 25), LocalDate.of(1993, 9, 5), LocalDate.of(1991, 11, 12),
                LocalDate.of(1987, 2, 18), LocalDate.of(1989, 4, 30), LocalDate.of(1994, 6, 8),
                LocalDate.of(1996, 8, 22)
        };
        int[] heights = {175, 162, 178, 172, 165, 160, 180, 170, 168, 163};
        double[] weights = {72.5, 55.0, 78.0, 68.0, 52.0, 50.0, 82.0, 70.0, 58.0, 54.0};

        String encodedPw = passwordEncoder.encode("1111");
        for (int i = 0; i < 10; i++) {
            Member m = Member.builder()
                    .email("user" + (i + 1) + "@desk.com")
                    .pw(encodedPw)
                    .name(names[i])
                    .gender(genders[i])
                    .birthDate(birthDates[i])
                    .height(heights[i])
                    .weight(weights[i])
                    .build();
            list.add(m);
        }
        return list;
    }

    private List<MemberInfoAddr> createMemberInfoAddrDummyData(List<Member> members) {
        List<MemberInfoAddr> list = new ArrayList<>();
        String[] zipcodes = {"04524", "06134", "08394", "10544", "13487", "15832", "16972", "18492", "20134", "21567"};
        String[] addr1 = {"서울 중구 세종대로 110", "서울 강남구 테헤란로 152", "서울 마포구 월드컵북로 396",
                "경기 성남시 분당구 판교역로 235", "인천 연수구 송도과학로 85", "대전 유성구 과학로 125",
                "광주 북구 첨단과기로 123", "대구 수성구 달구벌대로 509", "부산 해운대구 우동 1000", "울산 남구 삼산로 123"};
        for (int i = 0; i < members.size(); i++) {
            Member m = members.get(i);
            Long memberId = m.getId();
            if (memberId == null) continue;

            // 회원당 1개 배송지 (기본)
            list.add(MemberInfoAddr.builder()
                    .memberId(memberId)
                    .shipToName(m.getName())
                    .shipToPhone("010-1234-" + String.format("%04d", i + 1))
                    .shipZipcode(zipcodes[i % zipcodes.length])
                    .shipAddress1(addr1[i % addr1.length])
                    .shipAddress2((i % 2 == 0) ? "101동 " + (i + 1) + "호" : null)
                    .isDefault(true)
                    .build());

            // 절반 회원은 배송지 1개 더
            if (i % 2 == 0) {
                list.add(MemberInfoAddr.builder()
                        .memberId(memberId)
                        .shipToName(m.getName() + " (회사)")
                        .shipToPhone("02-1234-" + String.format("%04d", i + 1))
                        .shipZipcode("03123")
                        .shipAddress1("서울 종로구 종로 1")
                        .shipAddress2("본관 3층")
                        .isDefault(false)
                        .build());
            }
        }
        return list;
    }

    private List<MemberInfoBody> createMemberInfoBodyDummyData(List<Member> members) {
        List<MemberInfoBody> list = new ArrayList<>();
        ExercisePurpose[] purposes = ExercisePurpose.values();
        Instant base = LocalDateTime.of(2024, 6, 1, 10, 0).atZone(ZoneId.systemDefault()).toInstant();

        for (int mi = 0; mi < members.size(); mi++) {
            Member m = members.get(mi);
            Long memberId = m.getId();
            if (memberId == null) continue;

            double baseH = m.getHeight() != null ? m.getHeight().doubleValue() : 170.0;
            double baseW = m.getWeight() != null ? m.getWeight() : 65.0;

            for (int k = 0; k < 3; k++) {
                Instant measuredTime = base.plusSeconds(86400L * (mi * 30 + k * 14)); // 약 2주 간격
                double h = baseH + (Math.random() * 2 - 1);
                double w = baseW + (Math.random() * 3 - 1.5);
                double muscle = 30 + Math.random() * 15;
                double fatPct = 15 + Math.random() * 15;
                double water = 50 + Math.random() * 10;
                double protein = 10 + Math.random() * 5;
                double minerals = 3 + Math.random() * 2;
                double fatMass = w * fatPct / 100.0;
                double targetW = w + (Math.random() * 4 - 2);
                double weightCtrl = targetW - w;
                double fatCtrl = (fatPct - 18) * w / 100;
                double muscleCtrl = (25 - muscle / w * 100) * w / 100;
                ExercisePurpose purpose = purposes[k % purposes.length];

                list.add(MemberInfoBody.builder()
                        .memberId(memberId)
                        .height(round1(h))
                        .weight(round1(w))
                        .skeletalMuscleMass(round1(muscle))
                        .bodyFatPercent(round1(fatPct))
                        .bodyWater(round1(water))
                        .protein(round1(protein))
                        .minerals(round1(minerals))
                        .bodyFatMass(round1(fatMass))
                        .targetWeight(round1(targetW))
                        .weightControl(round1(weightCtrl))
                        .fatControl(round1(fatCtrl))
                        .muscleControl(round1(muscleCtrl))
                        .exercisePurpose(purpose)
                        .measuredTime(measuredTime)
                        .build());
            }
        }
        return list;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}

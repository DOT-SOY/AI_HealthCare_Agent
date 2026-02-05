package com.backend.data;

import com.backend.domain.member.Member;
import com.backend.domain.member.Member.Gender;
import com.backend.domain.meal.Meal;
import com.backend.domain.memberinfo.MemberInfoAddr;
import com.backend.domain.memberinfo.MemberInfoBody;
import com.backend.domain.memberinfo.MemberInfoBody.ExercisePurpose;
import com.backend.domain.routine.Routine;
import com.backend.domain.routine.RoutineStatus;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.memberinfo.MemberInfoAddrRepository;
import com.backend.repository.memberinfo.MemberInfoBodyRepository;
import com.backend.repository.meal.MealRepository;
import com.backend.repository.routine.RoutineRepository;
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
 * Member, MemberInfoAddr, MemberInfoBody, Routine, Meal 도메인에 대한 더미 데이터 삽입 테스트.
 * 랭킹 페이지(20명 + 루틴/식단 수행률)용 데이터를 DB에 insert 합니다. (@Rollback(false))
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("더미 데이터 삽입 테스트 (회원/배송지/신체정보/루틴/식단)")
class DummyDataInsertTest {

    private static final int MEMBER_COUNT = 20;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberInfoAddrRepository memberInfoAddrRepository;

    @Autowired
    private MemberInfoBodyRepository memberInfoBodyRepository;

    @Autowired
    private RoutineRepository routineRepository;

    @Autowired
    private MealRepository mealRepository;

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

        // 4) 루틴 더미 (최근 30일, 회원별 COMPLETED 비율 다르게 → 랭킹 순위 차이)
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(30);
        List<Routine> routines = createRoutineDummyData(allMembers, start, end);
        List<Routine> savedRoutines = routineRepository.saveAll(routines);
        routineRepository.flush();
        System.out.println("루틴 " + savedRoutines.size() + "건 삽입 완료.");

        // 5) 식단 더미 (동일 기간, 회원별 EATEN 비율 다르게 → 랭킹 순위 차이)
        List<Meal> meals = createMealDummyData(allMembers, start, end);
        List<Meal> savedMeals = mealRepository.saveAll(meals);
        mealRepository.flush();
        System.out.println("식단 " + savedMeals.size() + "건 삽입 완료.");

        assertThat(memberRepository.count()).isPositive();
        assertThat(savedAddrs).hasSize(addrs.size());
        assertThat(savedBodies).hasSize(bodies.size());
        assertThat(savedRoutines).hasSize(routines.size());
        assertThat(savedMeals).hasSize(meals.size());
    }

    private List<Member> createMemberDummyData() {
        List<Member> list = new ArrayList<>();
        String[] names = {
                "홍길동", "김영희", "이철수", "박민수", "최지영", "정수진", "강호동", "유재석", "송지은", "한소희",
                "윤서준", "임도현", "배성민", "오민지", "신동혁", "권나영", "황지훈", "서유나", "김도윤", "이서현"
        };
        Gender[] genders = {
                Gender.MALE, Gender.FEMALE, Gender.MALE, Gender.MALE, Gender.FEMALE,
                Gender.FEMALE, Gender.MALE, Gender.MALE, Gender.FEMALE, Gender.FEMALE,
                Gender.MALE, Gender.MALE, Gender.MALE, Gender.FEMALE, Gender.MALE,
                Gender.FEMALE, Gender.MALE, Gender.FEMALE, Gender.MALE, Gender.FEMALE
        };
        LocalDate[] birthDates = {
                LocalDate.of(1990, 1, 15), LocalDate.of(1992, 3, 20), LocalDate.of(1988, 5, 10),
                LocalDate.of(1995, 7, 25), LocalDate.of(1993, 9, 5), LocalDate.of(1991, 11, 12),
                LocalDate.of(1987, 2, 18), LocalDate.of(1989, 4, 30), LocalDate.of(1994, 6, 8),
                LocalDate.of(1996, 8, 22), LocalDate.of(1990, 2, 1), LocalDate.of(1991, 4, 15),
                LocalDate.of(1989, 6, 20), LocalDate.of(1994, 8, 10), LocalDate.of(1992, 10, 5),
                LocalDate.of(1993, 12, 25), LocalDate.of(1988, 3, 8), LocalDate.of(1995, 5, 18),
                LocalDate.of(1991, 7, 22), LocalDate.of(1996, 9, 30)
        };
        String encodedPw = passwordEncoder.encode("1111");
        for (int i = 0; i < MEMBER_COUNT; i++) {
            Member m = Member.builder()
                    .email("user" + (i + 1) + "@desk.com")
                    .pw(encodedPw)
                    .name(names[i])
                    .gender(genders[i])
                    .birthDate(birthDates[i])
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
        // 키/몸무게는 Member가 아닌 MemberInfoBody에서만 사용 (더미 base 값)
        int[] heights = {175, 162, 178, 172, 165, 160, 180, 170, 168, 163, 176, 174, 169, 164, 177, 161, 171, 167, 173, 166};
        double[] weights = {72.5, 55.0, 78.0, 68.0, 52.0, 50.0, 82.0, 70.0, 58.0, 54.0, 74.0, 71.0, 66.0, 53.0, 76.0, 51.0, 69.0, 57.0, 73.0, 56.0};

        for (int mi = 0; mi < members.size(); mi++) {
            Member m = members.get(mi);
            Long memberId = m.getId();
            if (memberId == null) continue;

            double baseH = mi < heights.length ? heights[mi] : 170.0;
            double baseW = mi < weights.length ? weights[mi] : 65.0;

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
                // 랭킹 비교용: 회원별 최신(마지막) 건만 목적을 다이어트/유지/벌크업에 나눠서 넣기
                ExercisePurpose purpose;
                if (k == 2) {
                    if (mi < 7) purpose = ExercisePurpose.DIET;
                    else if (mi < 14) purpose = ExercisePurpose.MAINTAIN;
                    else purpose = ExercisePurpose.BULK_UP;
                } else {
                    purpose = purposes[k % purposes.length];
                }

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

    /** 최근 30일 구간에 회원별 루틴 생성. 회원 인덱스에 따라 COMPLETED 비율을 다르게 해 랭킹 순위 차이. */
    private List<Routine> createRoutineDummyData(List<Member> members, LocalDate start, LocalDate end) {
        List<Routine> list = new ArrayList<>();

        for (int mi = 0; mi < members.size(); mi++) {
            Member m = members.get(mi);
            if (m.getId() == null) continue;
            // 회원별 COMPLETED 비율: 0번 90% → 19번 20% 정도 (역순으로 상위권)
            double completedRatio = 0.9 - (mi * 0.035); // 0.9, 0.865, ... ~0.235
            if (completedRatio < 0.2) completedRatio = 0.2;

            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(2)) { // 2일 간격으로 약 15건
                RoutineStatus status = Math.random() < completedRatio ? RoutineStatus.COMPLETED
                        : (Math.random() < 0.5 ? RoutineStatus.IN_PROGRESS : RoutineStatus.EXPECTED);
                list.add(Routine.builder()
                        .member(m)
                        .date(d)
                        .title("루틴 " + d)
                        .status(status)
                        .build());
            }
        }
        return list;
    }

    /** 최근 30일 구간에 회원별 식단 생성. 회원 인덱스에 따라 EATEN 비율을 다르게 해 랭킹 순위 차이. */
    private List<Meal> createMealDummyData(List<Member> members, LocalDate start, LocalDate end) {
        List<Meal> list = new ArrayList<>();
        Meal.MealTime[] mealTimes = {Meal.MealTime.BREAKFAST, Meal.MealTime.LUNCH, Meal.MealTime.DINNER};

        for (int mi = 0; mi < members.size(); mi++) {
            Member m = members.get(mi);
            Long memberId = m.getId();
            if (memberId == null) continue;
            // 회원별 EATEN 비율: 0번 높음 → 19번 낮음
            double eatenRatio = 0.85 - (mi * 0.032);
            if (eatenRatio < 0.25) eatenRatio = 0.25;

            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                for (Meal.MealTime mealTime : mealTimes) {
                    Meal.MealStatus status = Math.random() < eatenRatio ? Meal.MealStatus.EATEN
                            : (Math.random() < 0.5 ? Meal.MealStatus.PLANNED : Meal.MealStatus.SKIPPED);
                    list.add(Meal.builder()
                            .userId(memberId)
                            .mealDate(d)
                            .mealTime(mealTime)
                            .status(status)
                            .isAdditional(false)
                            .foodName("더미")
                            .calories(500)
                            .build());
                }
            }
        }
        return list;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}

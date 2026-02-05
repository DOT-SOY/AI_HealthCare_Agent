package com.backend.service.ranking;

import com.backend.domain.member.Member;
import com.backend.domain.meal.Meal;
import com.backend.domain.routine.Routine;
import com.backend.domain.routine.RoutineStatus;
import com.backend.domain.memberinfo.MemberInfoBody;
import com.backend.dto.response.RankingResponse;
import com.backend.dto.response.FilterInfo;
import com.backend.dto.response.RankingEntry;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.memberinfo.MemberInfoBodyRepository;
import com.backend.repository.meal.MealRepository;
import com.backend.repository.routine.RoutineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingServiceImpl implements RankingService {

    private final MemberRepository memberRepository;
    private final MemberInfoBodyRepository memberInfoBodyRepository;
    private final MealRepository mealRepository;
    private final RoutineRepository routineRepository;

    private static final double MEAL_WEIGHT = 0.5;
    private static final double ROUTINE_WEIGHT = 0.5;

    @Override
    public RankingResponse getRanking(String email, int limit) {

        // 0. 기준이 되는 회원 정보 조회 (성별/나이대 계산용)
        Member currentMember = memberRepository.findByEmail(email)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));

        String genderCode = currentMember.getGender() != null ? currentMember.getGender().name() : null;
        String ageGroupCodeForCurrent = resolveAgeGroup(currentMember);

        // 0-1. 기준 회원의 운동 목적 (최신 인바디 기준) 조회
        MemberInfoBody latestCurrentBody = memberInfoBodyRepository
                .findTopByMemberIdAndNotDeletedOrderByMeasuredTimeDesc(currentMember.getId())
                .orElse(null);

        if (latestCurrentBody == null || latestCurrentBody.getExercisePurpose() == null) {
            // 운동 목적 정보가 없다면, 비교 그룹을 만들 수 없으므로 빈 결과 반환
            FilterInfo emptyFilter = FilterInfo.builder()
                    .gender(genderCode)
                    .ageGroup(ageGroupCodeForCurrent)
                    .exercisePurpose(null)
                    .build();

            return RankingResponse.builder()
                    .topRanks(List.of())
                    .myRank(null)
                    .myScore(null)
                    .totalCount(0L)
                    .filterInfo(emptyFilter)
                    .build();
        }

        MemberInfoBody.ExercisePurpose purposeEnum = latestCurrentBody.getExercisePurpose();

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(6); // 최근 7일 (오늘 포함)

        // 1. 기준 회원과 동일한 성별 + 동일한 나이대 + 동일한 운동 목적을 가진 회원만 한 번에 조회
        List<Member> allMembers = filterMembersByFixedGroup(currentMember, purposeEnum);

        if (allMembers.isEmpty()) {
            FilterInfo filterInfo = FilterInfo.builder()
                    .gender(genderCode)
                    .ageGroup(ageGroupCodeForCurrent)
                    .exercisePurpose(purposeEnum.name())
                    .build();

            return RankingResponse.builder()
                    .topRanks(List.of())
                    .myRank(null)
                    .myScore(null)
                    .totalCount(0L)
                    .filterInfo(filterInfo)
                    .build();
        }

        // 2. 회원 ID 리스트 추출
        List<Long> memberIds = allMembers.stream()
                .map(Member::getId)
                .collect(Collectors.toList());

        // 3. 식단 데이터를 배치 조회하여 맵으로 구성 (N+1 문제 해결)
        List<Meal> allMeals = mealRepository.findByUserIdInAndMealDateBetween(memberIds, startDate, endDate);
        Map<Long, List<Meal>> mealsByMemberId = allMeals.stream()
                .collect(Collectors.groupingBy(Meal::getUserId));

        // 4. 루틴 데이터를 배치 조회하여 맵으로 구성 (N+1 문제 해결)
        List<Routine> allRoutines = routineRepository.findByMemberIdInAndDateBetween(memberIds, startDate, endDate);
        Map<Long, List<Routine>> routinesByMemberId = allRoutines.stream()
                .collect(Collectors.groupingBy(r -> r.getMember().getId()));

        // 5. 각 회원별 점수 계산
        long totalCount = allMembers.size();
        List<RankingEntry> rankingEntries = new ArrayList<>();

        for (Member member : allMembers) {
            Long memberId = member.getId();

            // 배치 조회한 데이터에서 해당 회원의 식단/루틴 가져오기
            List<Meal> meals = mealsByMemberId.getOrDefault(memberId, List.of());
            List<Routine> routines = routinesByMemberId.getOrDefault(memberId, List.of());

            double mealScore = calculateMealScore(meals);
            double routineScore = calculateRoutineScore(routines);
            double totalScore = mealScore * MEAL_WEIGHT + routineScore * ROUTINE_WEIGHT;

            String ageGroupCode = resolveAgeGroup(member);
            // 모든 회원은 이미 같은 목적(purposeEnum)으로 필터링되었으므로 통일
            String purposeCode = purposeEnum.name();

            RankingEntry entry = RankingEntry.builder()
                    .memberId(memberId)
                    .nickname(member.getName())
                    .gender(member.getGender() != null ? member.getGender().name() : null)
                    .ageGroup(ageGroupCode)
                    .exercisePurpose(purposeCode)
                    .mealScore(roundScore(mealScore))
                    .routineScore(roundScore(routineScore))
                    .totalScore(roundScore(totalScore))
                    .build();

            rankingEntries.add(entry);
        }

        // 5. 정렬 및 순위 부여
        rankingEntries.sort(Comparator
                .comparingDouble(RankingEntry::getTotalScore).reversed()
                .thenComparingDouble(RankingEntry::getMealScore).reversed()
                .thenComparingDouble(RankingEntry::getRoutineScore).reversed()
                .thenComparing(RankingEntry::getMemberId));

        List<RankingEntry> rankedEntries = new ArrayList<>();
        int currentRank = 1;
        for (RankingEntry entry : rankingEntries) {
            RankingEntry ranked = RankingEntry.builder()
                    .memberId(entry.getMemberId())
                    .nickname(entry.getNickname())
                    .gender(entry.getGender())
                    .ageGroup(entry.getAgeGroup())
                    .exercisePurpose(entry.getExercisePurpose())
                    .mealScore(entry.getMealScore())
                    .routineScore(entry.getRoutineScore())
                    .totalScore(entry.getTotalScore())
                    .rank(currentRank)
                    .build();
            rankedEntries.add(ranked);
            currentRank++;
        }

        // 6. 상위 N명 추출
        List<RankingEntry> topRanks = rankedEntries.stream()
                .limit(Math.max(limit, 1))
                .collect(Collectors.toList());

        // 7. 내 순위 / 내 점수 찾기
        Long currentMemberId = currentMember.getId();
        Optional<RankingEntry> myEntryOpt = rankedEntries.stream()
                .filter(e -> e.getMemberId().equals(currentMemberId))
                .findFirst();

        Integer myRank = myEntryOpt.map(RankingEntry::getRank).orElse(null);
        RankingEntry myScore = myEntryOpt.orElse(null);

        FilterInfo filterInfo = FilterInfo.builder()
                .gender(genderCode)
                .ageGroup(ageGroupCodeForCurrent)
                .exercisePurpose(purposeEnum.name())
                .build();

        return RankingResponse.builder()
                .topRanks(topRanks)
                .myRank(myRank)
                .myScore(myScore)
                .totalCount(totalCount)
                .filterInfo(filterInfo)
                .build();
    }

    /**
     * 기준 회원과 동일한 성별/나이대(10대 단위)이면서,
     * 동일한 운동 목적을 가진 회원만 한 번의 쿼리로 조회합니다.
     */
    private List<Member> filterMembersByFixedGroup(Member currentMember, MemberInfoBody.ExercisePurpose purposeEnum) {
        if (currentMember.getGender() == null || currentMember.getBirthDate() == null) {
            return List.of();
        }

        int currentYear = LocalDate.now().getYear();
        int age = currentYear - currentMember.getBirthDate().getYear();

        int minAge; // 같은 나이대의 최소 나이
        int maxAge; // 같은 나이대의 최대 나이
        if (age < 10) {
            // 랭킹 대상 연령이 아니라면, 동일 그룹을 찾지 않는다.
            return List.of();
        } else if (age < 20) {
            minAge = 10;
            maxAge = 19;
        } else if (age < 30) {
            minAge = 20;
            maxAge = 29;
        } else if (age < 40) {
            minAge = 30;
            maxAge = 39;
        } else if (age < 50) {
            minAge = 40;
            maxAge = 49;
        } else if (age < 60) {
            minAge = 50;
            maxAge = 59;
        } else {
            minAge = 60;
            maxAge = 150; // 충분히 큰 값으로 설정
        }

        int birthYearStart = currentYear - maxAge; // 가장 나이가 많은 연령대의 출생년도
        int birthYearEnd = currentYear - minAge;   // 가장 나이가 적은 연령대의 출생년도

        LocalDate birthDateStart = LocalDate.of(birthYearStart, 1, 1);
        LocalDate birthDateEnd = LocalDate.of(birthYearEnd, 12, 31);

        return memberRepository.findActiveGroupMembers(
                currentMember.getGender(),
                birthDateStart,
                birthDateEnd,
                purposeEnum
        );
    }

    private String resolveAgeGroup(Member member) {
        if (member.getBirthDate() == null) {
            return null;
        }
        int birthYear = member.getBirthDate().getYear();
        int currentYear = LocalDate.now().getYear();
        int age = currentYear - birthYear;

        if (age < 10) {
            return null;
        } else if (age < 20) {
            return "10s";
        } else if (age < 30) {
            return "20s";
        } else if (age < 40) {
            return "30s";
        } else if (age < 50) {
            return "40s";
        } else if (age < 60) {
            return "50s";
        } else {
            return "60plus";
        }
    }

    /**
     * 식단 점수 계산 (배치 조회된 데이터 사용)
     */
    private double calculateMealScore(List<Meal> meals) {
        long totalMeals = meals.size();
        if (totalMeals == 0) {
            return 0.0;
        }
        long eatenMeals = meals.stream()
                .filter(m -> m.getStatus() == Meal.MealStatus.EATEN)
                .count();

        double rate = (double) eatenMeals / (double) totalMeals;
        return rate * 100.0;
    }

    /**
     * 루틴 점수 계산 (배치 조회된 데이터 사용)
     */
    private double calculateRoutineScore(List<Routine> routines) {
        long totalRoutines = routines.size();
        if (totalRoutines == 0) {
            return 0.0;
        }
        long completedRoutines = routines.stream()
                .filter(r -> r.getStatus() == RoutineStatus.COMPLETED)
                .count();

        double rate = (double) completedRoutines / (double) totalRoutines;
        return rate * 100.0;
    }

    private double roundScore(double score) {
        return Math.round(score * 10.0) / 10.0; // 소수점 1자리까지
    }
}



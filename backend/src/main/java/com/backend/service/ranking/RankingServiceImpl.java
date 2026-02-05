package com.backend.service.ranking;

import com.backend.domain.member.Member;
import com.backend.domain.memberinfo.MemberInfoBody;
import com.backend.domain.meal.Meal;
import com.backend.domain.routine.Routine;
import com.backend.domain.routine.RoutineStatus;
import com.backend.dto.ranking.*;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.memberinfo.MemberInfoBodyRepository;
import com.backend.repository.meal.MealSearch;
import com.backend.repository.routine.RoutineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingServiceImpl implements RankingService {

    private final MemberRepository memberRepository;
    private final MemberInfoBodyRepository memberInfoBodyRepository;
    private final RoutineRepository routineRepository;
    private final MealSearch mealSearch;

    @Override
    public RankingResponseDto getRankingByPurpose(String currentUserEmail, Integer periodDays, LocalDate startDate, LocalDate endDate) {
        LocalDate end = (endDate != null) ? endDate : LocalDate.now();
        LocalDate start = (startDate != null) ? startDate : (periodDays != null ? end.minusDays(periodDays) : end.minusDays(30));
        if (periodDays != null && startDate == null && endDate == null) {
            end = LocalDate.now();
            start = end.minusDays(periodDays);
        }

        Long myMemberId = resolveMemberId(currentUserEmail);

        List<Object[]> purposeRows = memberInfoBodyRepository.findLatestMemberIdAndPurpose();
        Map<Long, MemberInfoBody.ExercisePurpose> memberPurposeMap = new HashMap<>();
        for (Object[] row : purposeRows) {
            Long mid = ((Number) row[0]).longValue();
            String goal = row[1] != null ? row[1].toString() : null;
            if (goal != null) {
                try {
                    memberPurposeMap.put(mid, MemberInfoBody.ExercisePurpose.valueOf(goal));
                } catch (Exception ignored) {
                }
            }
        }
        List<Long> memberIds = new ArrayList<>(memberPurposeMap.keySet());
        if (memberIds.isEmpty()) {
            return buildEmptyResponse();
        }

        List<RankingEntryDto> allEntries = new ArrayList<>();
        for (Long memberId : memberIds) {
            MemberInfoBody.ExercisePurpose purpose = memberPurposeMap.get(memberId);
            if (purpose == null) continue;

            double routineRate = computeRoutineRate(memberId, start, end);
            double mealRate = computeMealRate(memberId, start, end);
            double combinedRate = (routineRate + mealRate) / 2.0;
            String memberName = memberRepository.findById(memberId).map(Member::getName).orElse("회원");

            allEntries.add(RankingEntryDto.builder()
                    .rank(0)
                    .memberId(memberId)
                    .memberName(memberName)
                    .purpose(purpose.name())
                    .routineRate(routineRate)
                    .mealRate(mealRate)
                    .combinedRate(combinedRate)
                    .build());
        }

        Map<String, List<RankingEntryDto>> byPurpose = allEntries.stream().collect(Collectors.groupingBy(RankingEntryDto::getPurpose));
        Map<String, RankingGroupDto> groups = new LinkedHashMap<>();
        for (String purpose : new String[]{"DIET", "MAINTAIN", "BULK_UP"}) {
            List<RankingEntryDto> list = byPurpose.getOrDefault(purpose, Collections.emptyList());
            list.sort(Comparator.comparing(RankingEntryDto::getCombinedRate).reversed());
            for (int i = 0; i < list.size(); i++) {
                RankingEntryDto e = list.get(i);
                list.set(i, RankingEntryDto.builder()
                        .rank(i + 1)
                        .memberId(e.getMemberId())
                        .memberName(e.getMemberName())
                        .purpose(e.getPurpose())
                        .routineRate(e.getRoutineRate())
                        .mealRate(e.getMealRate())
                        .combinedRate(e.getCombinedRate())
                        .build());
            }
            List<RankingEntryDto> top3 = list.size() >= 3 ? new ArrayList<>(list.subList(0, 3)) : new ArrayList<>(list);
            groups.put(purpose, RankingGroupDto.builder()
                    .exercisePurpose(purpose)
                    .totalCount(list.size())
                    .top3(top3)
                    .fullList(list)
                    .build());
        }

        String myPurpose = null;
        Integer myRankInGroup = null;
        Integer myGroupSize = null;
        Double myRoutineRate = null;
        Double myMealRate = null;
        Double myCombinedRate = null;
        MemberInfoBody.ExercisePurpose myPurposeEnum = memberPurposeMap.get(myMemberId);
        if (myPurposeEnum != null) {
            myPurpose = myPurposeEnum.name();
            RankingGroupDto myGroup = groups.get(myPurpose);
            if (myGroup != null) {
                myGroupSize = myGroup.getTotalCount();
                Optional<RankingEntryDto> me = myGroup.getFullList().stream()
                        .filter(e -> e.getMemberId().equals(myMemberId)).findFirst();
                if (me.isPresent()) {
                    myRankInGroup = me.get().getRank();
                    myRoutineRate = me.get().getRoutineRate();
                    myMealRate = me.get().getMealRate();
                    myCombinedRate = me.get().getCombinedRate();
                }
            }
        }

        return RankingResponseDto.builder()
                .myPurpose(myPurpose)
                .myRankInGroup(myRankInGroup)
                .myGroupSize(myGroupSize)
                .myRoutineRate(myRoutineRate)
                .myMealRate(myMealRate)
                .myCombinedRate(myCombinedRate)
                .groups(groups)
                .build();
    }

    private double computeRoutineRate(Long memberId, LocalDate start, LocalDate end) {
        List<Routine> routines = routineRepository.findByMemberIdAndDateBetween(memberId, start, end);
        if (routines.isEmpty()) return 0.0;
        long completed = routines.stream().filter(r -> r.getStatus() == RoutineStatus.COMPLETED).count();
        return (completed * 100.0) / routines.size();
    }

    private double computeMealRate(Long memberId, LocalDate start, LocalDate end) {
        List<Meal> meals = mealSearch.findMealsBetweenDates(memberId, start, end);
        if (meals.isEmpty()) return 0.0;
        long eaten = meals.stream().filter(m -> m.getStatus() == Meal.MealStatus.EATEN).count();
        return (eaten * 100.0) / meals.size();
    }

    private Long resolveMemberId(String email) {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("인증된 사용자 이메일이 없습니다.");
        return memberRepository.findByEmail(email)
                .filter(m -> !m.isDeleted())
                .map(Member::getId)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));
    }

    private RankingResponseDto buildEmptyResponse() {
        Map<String, RankingGroupDto> empty = new LinkedHashMap<>();
        for (String p : new String[]{"DIET", "MAINTAIN", "BULK_UP"}) {
            empty.put(p, RankingGroupDto.builder()
                    .exercisePurpose(p).totalCount(0).top3(Collections.emptyList()).fullList(Collections.emptyList()).build());
        }
        return RankingResponseDto.builder()
                .myPurpose(null).myRankInGroup(null).myGroupSize(0)
                .myRoutineRate(null).myMealRate(null).myCombinedRate(null)
                .groups(empty).build();
    }
}

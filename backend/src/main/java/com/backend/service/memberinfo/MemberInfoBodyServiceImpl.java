package com.backend.service.memberinfo;

import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.domain.member.Member;
import com.backend.domain.meal.Meal;
import com.backend.domain.memberinfo.MemberInfoBody;
import com.backend.domain.memberinfo.MemberInfoBody.DataSource;
import com.backend.dto.memberinfo.BodyCompareFeedbackDTO;
import com.backend.dto.memberinfo.MemberInfoBodyDTO;
import com.backend.dto.memberinfo.MemberInfoBodyResponseDTO;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.meal.MealRepository;
import com.backend.repository.memberinfo.MemberInfoBodyRepository;
import com.backend.repository.routine.RoutineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Transactional
@Log4j2
public class MemberInfoBodyServiceImpl implements MemberInfoBodyService {

    private final MemberInfoBodyRepository memberInfoBodyRepository;
    private final MemberRepository memberRepository;
    private final MealRepository mealRepository;
    private final RoutineRepository routineRepository;

    @Override
    public Long create(Long memberId, MemberInfoBodyDTO dto) {
        log.info("신체 정보 생성 요청: memberId={}", memberId);

        fillHeightFromLatestIfNull(memberId, dto);
        dto.setDataSource(DataSource.MANUAL);
        MemberInfoBody entity = dto.toEntity(memberId);
        MemberInfoBody saved = memberInfoBodyRepository.save(entity);

        log.info("신체 정보 생성 완료: id={}", saved.getId());
        return saved.getId();
    }

    @Override
    public MemberInfoBodyResponseDTO update(Long id, MemberInfoBodyDTO dto) {
        log.info("신체 정보 수정 요청: id={}", id);

        MemberInfoBody entity = memberInfoBodyRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, id));

        Double heightToUse = dto.getHeight() != null ? dto.getHeight() : entity.getHeight();
        entity.update(
                heightToUse, dto.getWeight(),
                dto.getSkeletalMuscleMass(), dto.getBodyFatPercent(),
                dto.getBodyWater(), dto.getProtein(), dto.getMinerals(), dto.getBodyFatMass(),
                dto.getTargetWeight(), dto.getWeightControl(), dto.getFatControl(), dto.getMuscleControl(),
                dto.getExercisePurpose(),
                DataSource.MANUAL
        );

        MemberInfoBody saved = memberInfoBodyRepository.save(entity);
        log.info("신체 정보 수정 완료: id={}", id);

        MemberInfoBodyResponseDTO responseDto = MemberInfoBodyResponseDTO.fromEntity(saved);
        fillComputedControlValues(responseDto);
        return responseDto;
    }

    @Override
    public void delete(Long id) {
        log.info("신체 정보 삭제 요청: id={}", id);

        MemberInfoBody entity = memberInfoBodyRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, id));

        entity.softDelete();
        memberInfoBodyRepository.save(entity);

        log.info("신체 정보 삭제 완료: id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberInfoBodyResponseDTO> getHistory(Long memberId) {
        log.info("신체 정보 이력 조회 요청: memberId={}", memberId);

        List<MemberInfoBody> entities = memberInfoBodyRepository
                .findByMemberIdAndNotDeletedOrderByMeasuredTimeDesc(memberId);

        // Member 정보 조회 (한 번만 조회)
        Member member = memberRepository.findById(memberId).orElse(null);

        return entities.stream()
                .map(entity -> {
                    MemberInfoBodyResponseDTO dto = MemberInfoBodyResponseDTO.fromEntityWithMember(entity, member);
                    fillComputedControlValues(dto);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public MemberInfoBodyResponseDTO getLatest(Long memberId) {
        log.info("최신 신체 정보 조회 요청: memberId={}", memberId);

        MemberInfoBody entity = memberInfoBodyRepository
                .findFirstByMemberIdAndDeletedAtIsNullOrderByMeasuredTimeDescCreatedAtDesc(memberId)
                .orElse(null);

        // Member 정보 조회
        Member member = memberRepository.findById(memberId).orElse(null);

        MemberInfoBodyResponseDTO dto = MemberInfoBodyResponseDTO.fromEntityWithMember(entity, member);
        fillComputedControlValues(dto);
        return dto;
    }

    @Override
    public BodyCompareFeedbackDTO saveAndCompare(Long memberId, MemberInfoBodyDTO dto) {
        log.info("신체 정보 저장 후 직전 데이터와 비교: memberId={}", memberId);

        fillHeightFromLatestIfNull(memberId, dto);
        dto.setDataSource(DataSource.OCR);
        // 1. 저장
        MemberInfoBody entity = dto.toEntity(memberId);
        MemberInfoBody saved = memberInfoBodyRepository.save(entity);

        // 2. 해당 회원 최신 2건 조회 (방금 저장한 것 = current, 그 이전 = previous)
        List<MemberInfoBody> history = memberInfoBodyRepository
                .findByMemberIdAndNotDeletedOrderByMeasuredTimeDesc(memberId);
        MemberInfoBody current = history.isEmpty() ? null : history.get(0);
        MemberInfoBody previous = history.size() > 1 ? history.get(1) : null;

        return buildCompareFeedback(previous, current);
    }

    /**
     * 직전 1 row와 규칙 기반 비교 (체중 0.5kg, 체지방률 0.5%, 골격근량 0.2kg)
     * 식단/운동 7일 수집 없음 → mealFeedback, exerciseFeedback 빈 문자열 또는 안내 문구
     */
    private BodyCompareFeedbackDTO buildCompareFeedback(MemberInfoBody previous, MemberInfoBody current) {
        List<BodyCompareFeedbackDTO.BodyChangeItem> bodyChanges = new ArrayList<>();
        String summary;
        boolean hasComparison = (previous != null && current != null);

        if (hasComparison) {
            double wPrev = nullToZero(previous.getWeight());
            double wCur = nullToZero(current.getWeight());
            double diffW = wCur - wPrev;
            if (Math.abs(diffW) >= 0.5) {
                String dir = diffW > 0 ? "증가" : "감소";
                bodyChanges.add(BodyCompareFeedbackDTO.BodyChangeItem.builder()
                        .type("weight")
                        .change(dir)
                        .value(String.format("%s%.1fkg", diffW > 0 ? "+" : "", diffW))
                        .message(String.format("체중 %s (%.1fkg → %.1fkg)", dir, wPrev, wCur))
                        .build());
            }

            double fPrev = nullToZero(previous.getBodyFatPercent());
            double fCur = nullToZero(current.getBodyFatPercent());
            double diffF = fCur - fPrev;
            if (Math.abs(diffF) >= 0.5) {
                String dir = diffF > 0 ? "증가" : "감소";
                bodyChanges.add(BodyCompareFeedbackDTO.BodyChangeItem.builder()
                        .type("bodyFatPercent")
                        .change(dir)
                        .value(String.format("%s%.1f%%", diffF > 0 ? "+" : "", diffF))
                        .message(String.format("체지방률 %s (%.1f%% → %.1f%%)", dir, fPrev, fCur))
                        .build());
            }

            double mPrev = nullToZero(previous.getSkeletalMuscleMass());
            double mCur = nullToZero(current.getSkeletalMuscleMass());
            double diffM = mCur - mPrev;
            if (Math.abs(diffM) >= 0.2) {
                String dir = diffM > 0 ? "증가" : "감소";
                bodyChanges.add(BodyCompareFeedbackDTO.BodyChangeItem.builder()
                        .type("skeletalMuscleMass")
                        .change(dir)
                        .value(String.format("%s%.1fkg", diffM > 0 ? "+" : "", diffM))
                        .message(String.format("골격근량 %s (%.1fkg → %.1fkg)", dir, mPrev, mCur))
                        .build());
            }

            summary = bodyChanges.isEmpty()
                    ? "이전 측정과 큰 변화는 없습니다."
                    : "이전 대비 " + bodyChanges.size() + "가지 체성분 변화가 있습니다.";
        } else {
            // 분석만 (직전 데이터 없음)
            if (current != null) {
                List<String> parts = new ArrayList<>();
                if (current.getWeight() != null) parts.add("체중 " + current.getWeight() + "kg");
                if (current.getBodyFatPercent() != null) parts.add("체지방률 " + current.getBodyFatPercent() + "%");
                if (current.getSkeletalMuscleMass() != null) parts.add("골격근량 " + current.getSkeletalMuscleMass() + "kg");
                summary = parts.isEmpty() ? "측정값을 확인해보세요." : "현재 측정: " + String.join(", ", parts) + ".";
            } else {
                summary = "분석할 수치를 추출하지 못했습니다.";
            }
        }

        List<String> recommendations = new ArrayList<>();
        for (BodyCompareFeedbackDTO.BodyChangeItem item : bodyChanges) {
            if ("bodyFatPercent".equals(item.getType()) && "증가".equals(item.getChange())) {
                recommendations.add("유산소 운동을 꾸준히 해보세요.");
            } else if ("bodyFatPercent".equals(item.getType()) && "감소".equals(item.getChange())) {
                recommendations.add("체지방률이 줄어든 좋은 변화입니다.");
            } else if ("skeletalMuscleMass".equals(item.getType()) && "증가".equals(item.getChange())) {
                recommendations.add("근육량이 늘었습니다. 꾸준히 유지해보세요.");
            } else if ("skeletalMuscleMass".equals(item.getType()) && "감소".equals(item.getChange())) {
                recommendations.add("단백질 섭취와 근력 운동을 함께 해보세요.");
            }
        }

        // 옵션1: 직전 측정일 ~ 이번 측정일 사이 식단/운동 루틴 집계 피드백
        String mealFeedback = "";
        String exerciseFeedback = "";
        if (hasComparison) {
            Long memberId = current.getMemberId();
            ZoneId zone = ZoneId.systemDefault();
            LocalDate startDate = previous.getMeasuredTime() != null
                    ? previous.getMeasuredTime().atZone(zone).toLocalDate()
                    : null;
            LocalDate endDate = current.getMeasuredTime() != null
                    ? current.getMeasuredTime().atZone(zone).toLocalDate()
                    : null;

            if (startDate != null && endDate != null) {
                if (endDate.isBefore(startDate)) {
                    LocalDate tmp = startDate;
                    startDate = endDate;
                    endDate = tmp;
                }
                mealFeedback = buildMealFeedback(memberId, startDate, endDate);
                exerciseFeedback = buildExerciseFeedback(memberId, startDate, endDate);
            } else {
                mealFeedback = "식단: 측정일 정보가 부족하여 비교할 수 없어요.";
                exerciseFeedback = "운동: 측정일 정보가 부족하여 비교할 수 없어요.";
            }
        }

        return BodyCompareFeedbackDTO.builder()
                .summary(summary)
                .bodyChanges(bodyChanges)
                .mealFeedback(mealFeedback)
                .exerciseFeedback(exerciseFeedback)
                .recommendations(recommendations)
                .hasComparison(hasComparison)
                .build();
    }

    private static double nullToZero(Double v) {
        return v == null ? 0.0 : v;
    }

    private String buildMealFeedback(Long userId, LocalDate start, LocalDate end) {
        List<Meal> meals = mealRepository.findByUserIdAndMealDateBetween(userId, start, end);
        long days = ChronoUnit.DAYS.between(start, end) + 1;

        if (meals == null || meals.isEmpty()) {
            return String.format("식단: %s~%s 기간에 기록이 없어 비교가 어려워요. 식단을 기록해보세요.", start, end);
        }

        int eatenCount = 0;
        int skippedCount = 0;
        int totalCalories = 0;
        int totalProtein = 0;
        int totalCarbs = 0;
        int totalFat = 0;

        for (Meal m : meals) {
            if (m.getStatus() == Meal.MealStatus.EATEN) {
                eatenCount++;
                totalCalories += (m.getCalories() != null ? m.getCalories() : 0);
                totalProtein += (m.getProtein() != null ? m.getProtein() : 0);
                totalCarbs += (m.getCarbs() != null ? m.getCarbs() : 0);
                totalFat += (m.getFat() != null ? m.getFat() : 0);
            } else if (m.getStatus() == Meal.MealStatus.SKIPPED) {
                skippedCount++;
            }
        }

        int avgCalories = days > 0 ? (int) Math.round(totalCalories / (double) days) : totalCalories;
        int avgProtein = days > 0 ? (int) Math.round(totalProtein / (double) days) : totalProtein;

        return String.format(
                "식단: %s~%s (%d일) 동안 섭취 %d건, 건너뜀 %d건. 일 평균 약 %dkcal / 단백질 %dg (탄%d·단%d·지%d) 기록이 있어요.",
                start, end, days, eatenCount, skippedCount,
                avgCalories, avgProtein, totalCarbs, totalProtein, totalFat
        );
    }

    private String buildExerciseFeedback(Long memberId, LocalDate start, LocalDate end) {
        var routines = routineRepository.findByMemberIdAndDateBetween(memberId, start, end);
        long days = ChronoUnit.DAYS.between(start, end) + 1;

        if (routines == null || routines.isEmpty()) {
            return String.format("운동: %s~%s 기간에 루틴 기록이 없어 비교가 어려워요. 루틴을 기록/완료해보세요.", start, end);
        }

        long routineDays = routines.stream().map(r -> r.getDate()).distinct().count();
        long completedDays = routines.stream()
                .filter(r -> r.getStatus() == com.backend.domain.routine.RoutineStatus.COMPLETED)
                .map(r -> r.getDate())
                .distinct()
                .count();
        long completedExercises = routines.stream()
                .flatMap(r -> r.getExercises() != null ? r.getExercises().stream() : java.util.stream.Stream.empty())
                .filter(com.backend.domain.exercise.Exercise::isCompleted)
                .count();

        return String.format(
                "운동: %s~%s (%d일) 중 루틴 %d일 기록, 완료 %d일. 완료한 운동(세트) %d개가 있어요.",
                start, end, days, routineDays, completedDays, completedExercises
        );
    }

    /**
     * DTO의 키가 null일 때 해당 회원 최신 신체정보의 키로 채움 (OCR 등에서 키 미입력 시)
     */
    private void fillHeightFromLatestIfNull(Long memberId, MemberInfoBodyDTO dto) {
        if (dto == null || dto.getHeight() != null) return;
        memberInfoBodyRepository
                .findFirstByMemberIdAndDeletedAtIsNullOrderByMeasuredTimeDescCreatedAtDesc(memberId)
                .filter(latest -> latest.getHeight() != null)
                .ifPresent(latest -> dto.setHeight(latest.getHeight()));
    }

    /**
     * 키·몸무게·체지방률·골격근량으로 적정체중 및 조절량 계산 후 DTO에 반영.
     * - 적정체중: BMI 22 기준 (키만 사용) → 22 * (height/100)²
     * - 체중조절: 적정체중 - 현재체중
     * - 지방조절: 목표 체지방량(적정체중*18%) - 현재 체지방량
     * - 근육조절: 목표 골격근량(적정 제지방량*45%) - 현재 골격근량
     */
    private static void fillComputedControlValues(MemberInfoBodyResponseDTO dto) {
        if (dto == null) return;
        Double height = dto.getHeight();
        Double weight = dto.getWeight();

        // 적정체중: 키만 있으면 BMI 22 기준으로 계산
        double idealWeight = 0.0;
        if (height != null && dto.getTargetWeight() == null) {
            double heightM = height / 100.0;
            idealWeight = 22.0 * heightM * heightM;
            dto.setTargetWeight(round1(idealWeight));
        }

        // 체중·지방·근육 조절량: 값이 비어 있을 때만 계산 (OCR 값이 있으면 그대로 사용)
        if (height == null || weight == null) {
            if (dto.getWeightControl() == null) dto.setWeightControl(0.0);
            if (dto.getFatControl() == null) dto.setFatControl(0.0);
            if (dto.getMuscleControl() == null) dto.setMuscleControl(0.0);
            return;
        }

        if (dto.getWeightControl() == null || dto.getFatControl() == null || dto.getMuscleControl() == null) {
            double heightM = height / 100.0;
            idealWeight = idealWeight > 0 ? idealWeight : 22.0 * heightM * heightM;

            double weightControl = idealWeight - weight;
            if (dto.getWeightControl() == null) dto.setWeightControl(round1(weightControl));

            double targetFatRatio = 0.18;
            double targetFatMass = idealWeight * targetFatRatio;
            double currentFatMass = weight * (dto.getBodyFatPercent() != null ? dto.getBodyFatPercent() / 100.0 : 0.0);
            if (dto.getFatControl() == null) dto.setFatControl(round1(targetFatMass - currentFatMass));

            double targetLeanMass = idealWeight * (1 - targetFatRatio);
            double targetSkeletalMuscle = targetLeanMass * 0.45;
            double currentMuscle = dto.getSkeletalMuscleMass() != null ? dto.getSkeletalMuscleMass() : 0.0;
            if (dto.getMuscleControl() == null) dto.setMuscleControl(round1(targetSkeletalMuscle - currentMuscle));
        }
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    @Override
    @Transactional(readOnly = true)
    public MemberInfoBodyResponseDTO getBodyInfoByDateAndMetric(Long memberId, LocalDate date, String metric) {
        log.info("인바디 조회 요청: memberId={}, date={}, metric={}", memberId, date, metric);

        LocalDate targetDate = date != null ? date : LocalDate.now();

        // 날짜의 시작과 끝 시간 계산
        Instant dateStart = targetDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant dateEnd = targetDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        Optional<MemberInfoBody> entityOpt = memberInfoBodyRepository.findByMemberIdAndDate(
            memberId, dateStart, dateEnd
        );

        if (entityOpt.isEmpty()) {
            log.info("해당 날짜의 인바디 기록이 없습니다: memberId={}, date={}", memberId, targetDate);
            return null;
        }

        MemberInfoBody entity = entityOpt.get();
        Member member = memberRepository.findById(memberId).orElse(null);
        MemberInfoBodyResponseDTO dto = MemberInfoBodyResponseDTO.fromEntityWithMember(entity, member);
        fillComputedControlValues(dto);

        // 특정 항목만 조회하는 경우 필터링
        if (metric != null && !metric.trim().isEmpty()) {
            MemberInfoBodyResponseDTO filteredDto = MemberInfoBodyResponseDTO.builder()
                .id(dto.getId())
                .memberId(dto.getMemberId())
                .measuredTime(dto.getMeasuredTime())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .memberName(dto.getMemberName())
                .gender(dto.getGender())
                .birthDate(dto.getBirthDate())
                .build();

            switch (metric.toUpperCase()) {
                case "BODY_FAT":
                    filteredDto.setBodyFatPercent(dto.getBodyFatPercent());
                    filteredDto.setBodyFatMass(dto.getBodyFatMass());
                    break;
                case "SKELETAL_MUSCLE":
                    filteredDto.setSkeletalMuscleMass(dto.getSkeletalMuscleMass());
                    break;
                case "WEIGHT":
                    filteredDto.setWeight(dto.getWeight());
                    break;
                default:
                    log.warn("알 수 없는 metric: {}, 모든 항목 반환", metric);
                    return dto;
            }

            return filteredDto;
        }

        // 모든 항목 반환
        return dto;
    }
}


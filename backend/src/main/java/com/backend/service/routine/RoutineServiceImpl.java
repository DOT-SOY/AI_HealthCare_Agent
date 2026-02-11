package com.backend.service.routine;

import com.backend.domain.exercise.Exercise;
import com.backend.domain.exercise.ExerciseCategory;
import com.backend.domain.exercise.ExerciseType;
import com.backend.domain.member.Member;
import com.backend.domain.routine.Routine;
import com.backend.domain.routine.RoutineStatus;
import com.backend.dto.request.CreateRoutinesFromRecommendationRequest;
import com.backend.dto.request.ExerciseAddRequest;
import com.backend.dto.request.ExerciseUpdateRequest;
import com.backend.dto.request.PainModifyApplyRequest;
import com.backend.dto.request.RoutineCreateRequest;
import com.backend.dto.response.ExerciseResponse;
import com.backend.dto.response.RoutineResponse;
import com.backend.dto.response.RoutinePresetDayDto;
import com.backend.dto.response.RoutinePresetGroupDto;
import com.backend.dto.response.VolumeStatsResponse;
import com.backend.repository.exercise.ExerciseRepository;
import com.backend.repository.exercise.ExerciseTypeRepository;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.routine.RoutineRepository;
import com.backend.service.pain.WorkoutReviewService;
import com.backend.util.BodyPartMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoutineServiceImpl implements RoutineService {

    private final RoutineRepository routineRepository;
    private final ExerciseRepository exerciseRepository;
    private final ExerciseTypeRepository exerciseTypeRepository;
    private final MemberRepository memberRepository;
    private final WorkoutReviewService workoutReviewService;
    private final SimpMessagingTemplate messagingTemplate;

    /** 운동명 → category (9종목만 사용) */
    private static final Map<String, String> EXERCISE_NAME_TO_CATEGORY = new HashMap<>();
    static {
        EXERCISE_NAME_TO_CATEGORY.put("데드리프트", "BACK");
        EXERCISE_NAME_TO_CATEGORY.put("벤치프레스", "CHEST");
        EXERCISE_NAME_TO_CATEGORY.put("오버헤드프레스", "SHOULDER");
        EXERCISE_NAME_TO_CATEGORY.put("바벨 컬", "ARM");
        EXERCISE_NAME_TO_CATEGORY.put("플랭크", "CORE");
        EXERCISE_NAME_TO_CATEGORY.put("행잉레그레이즈", "ABS");
        EXERCISE_NAME_TO_CATEGORY.put("힙쓰러스트", "GLUTE");
        EXERCISE_NAME_TO_CATEGORY.put("스쿼트", "THIGH");
        EXERCISE_NAME_TO_CATEGORY.put("카프레이즈", "CALF");
    }

    /** 카드 1: 분할 4일 (Push → Pull → Leg → Core Day) */
    private static final List<RoutinePresetDayDto> PRESET_GROUP_0 = Arrays.asList(
        RoutinePresetDayDto.builder().title("Push Day").summary("가슴, 어깨, 삼두근을 사용하는 날입니다.").exerciseNames(Arrays.asList("벤치프레스", "오버헤드프레스")).build(),
        RoutinePresetDayDto.builder().title("Pull Day").summary("등, 이두근, 후면 사슬을 사용하는 날입니다.").exerciseNames(Arrays.asList("데드리프트", "바벨 컬")).build(),
        RoutinePresetDayDto.builder().title("Leg Day").summary("허벅지 앞/뒤, 엉덩이, 종아리를 사용하는 날입니다.").exerciseNames(Arrays.asList("스쿼트", "힙쓰러스트", "카프레이즈")).build(),
        RoutinePresetDayDto.builder().title("Core Day").summary("복부와 허리, 몸의 중심을 지탱하는 코어 근육을 사용하는 날입니다.").exerciseNames(Arrays.asList("플랭크", "행잉레그레이즈")).build()
    );
    /** 카드 2: 상하체 2일 */
    private static final List<RoutinePresetDayDto> PRESET_GROUP_1 = Arrays.asList(
        RoutinePresetDayDto.builder().title("Upper Day").summary("가슴, 어깨, 팔, 그리고 복근을 단련합니다.").exerciseNames(Arrays.asList("벤치프레스", "오버헤드프레스", "바벨 컬", "행잉레그레이즈", "플랭크")).build(),
        RoutinePresetDayDto.builder().title("Leg Day").summary("허벅지, 엉덩이, 종아리, 등 하부(후면 사슬)를 단련합니다.").exerciseNames(Arrays.asList("스쿼트", "데드리프트", "힙쓰러스트", "카프레이즈")).build()
    );

    @Override
    @Transactional(readOnly = true)
    public RoutineResponse getTodayRoutine(Long memberId) {
        LocalDate today = LocalDate.now();
        log.info("오늘의 루틴 조회: memberId={}, date={}", memberId, today);
        
        Routine routine = routineRepository.findByDateAndMemberId(today, memberId)
            .orElse(null);
        
        if (routine == null) {
            log.warn("오늘의 루틴을 찾을 수 없습니다: memberId={}, date={}", memberId, today);
            return null;
        }
        
        // JOIN FETCH로 이미 로드되었으므로 추가 쿼리 없음
        log.info("오늘의 루틴 조회 성공: routineId={}, exercisesCount={}", 
            routine.getId(), 
            routine.getExercises().size());
        
        return toRoutineResponse(routine, true);
    }

    @Override
    @Transactional(readOnly = true)
    public RoutineResponse getRoutineByDate(Long memberId, LocalDate date) {
        log.info("특정 날짜 루틴 조회: memberId={}, date={}", memberId, date);

        Routine routine = routineRepository.findByDateAndMemberId(date, memberId)
            .orElse(null);

        if (routine == null) {
            log.warn("해당 날짜의 루틴을 찾을 수 없습니다: memberId={}, date={}", memberId, date);
            return null;
        }

        return toRoutineResponse(routine, date.equals(LocalDate.now()));
    }
    
    @Override
    @Transactional(readOnly = true)
    public RoutineResponse getRoutineByDateWithFilters(Long memberId, LocalDate date, String exerciseName, Boolean completed) {
        log.info("특정 날짜 루틴 조회 (필터링): memberId={}, date={}, exerciseName={}, completed={}",
            memberId, date, exerciseName, completed);

        Routine routine = routineRepository.findByDateAndMemberId(date, memberId)
            .orElse(null);

        if (routine == null) {
            log.warn("해당 날짜의 루틴을 찾을 수 없습니다: memberId={}, date={}", memberId, date);
            return null;
        }

        // 필터링이 필요한 경우 루틴 복사 및 필터링
        if (exerciseName != null || completed != null) {
            Routine filteredRoutine = filterRoutine(routine, exerciseName, completed);
            if (filteredRoutine == null || filteredRoutine.getExercises().isEmpty()) {
                log.info("필터링 결과 운동이 없습니다: memberId={}, date={}, exerciseName={}, completed={}",
                    memberId, date, exerciseName, completed);
                return null;
            }
            return toRoutineResponse(filteredRoutine, date.equals(LocalDate.now()));
        }

        return toRoutineResponse(routine, date.equals(LocalDate.now()));
    }

    /**
     * 루틴의 운동 목록을 필터링합니다.
     */
    private Routine filterRoutine(Routine routine, String exerciseName, Boolean completed) {
        Routine filteredRoutine = new Routine();
        filteredRoutine.setId(routine.getId());
        filteredRoutine.setMember(routine.getMember());
        filteredRoutine.setDate(routine.getDate());
        filteredRoutine.setTitle(routine.getTitle());
        filteredRoutine.setAiSummary(routine.getAiSummary());
        filteredRoutine.setStatus(routine.getStatus());
        filteredRoutine.setExercises(new ArrayList<>());

        for (Exercise exercise : routine.getExercises()) {
            // 운동 이름 필터링
            if (exerciseName != null) {
                String exName = exercise.getExerciseType() != null ? exercise.getExerciseType().getName() : null;
                if (exName == null || !exName.equals(exerciseName)) {
                    continue;
                }
            }

            // 완료 상태 필터링
            if (completed != null && exercise.isCompleted() != completed) {
                continue;
            }

            filteredRoutine.getExercises().add(exercise);
        }

        return filteredRoutine;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoutineResponse> getWeeklyRoutines(Long memberId) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(3);
        LocalDate weekEnd = today.plusDays(3);
        
        log.info("주간 루틴 조회: memberId={}, weekStart={}, today={}, weekEnd={}", memberId, weekStart, today, weekEnd);
        
        List<Routine> routines = routineRepository.findByMemberIdAndDateBetween(memberId, weekStart, weekEnd);
        
        log.info("주간 루틴 조회 결과: routinesCount={}", routines.size());
        
        return routines.stream()
            .map(routine -> toRoutineResponse(routine, routine.getDate().equals(today)))
            .sorted(Comparator.comparing(RoutineResponse::getDate))
            .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<RoutineResponse> getHistory(Long memberId, String bodyPart) {
        // 최근 3개월 과거 ~ 3개월 미래까지의 루틴 조회 (완료 여부 및 예정 여부 무관)
        List<Routine> routines = routineRepository.findByMemberIdAndDateBetween(
            memberId,
            LocalDate.now().minusMonths(3),
            LocalDate.now().plusMonths(3)
        );
        
        log.info("기록 조회: memberId={}, bodyPart={}, 전체 루틴 수={}", memberId, bodyPart, routines.size());
        
        // JOIN FETCH로 이미 로드되었으므로 추가 쿼리 없음
        // 운동이 있는 루틴만 필터링 (완료 여부 무관)
        List<Routine> routinesWithExercises = routines.stream()
            .filter(routine -> routine.getExercises() != null && !routine.getExercises().isEmpty())
            .collect(Collectors.toList());
        
        // bodyPart 필터링 (메인 타겟 또는 서브 타겟 포함)
        if (bodyPart != null && !bodyPart.isEmpty() && !bodyPart.equals("전체")) {
            ExerciseCategory targetCategory = mapBodyPartToCategory(bodyPart);
            if (targetCategory != null) {
                routinesWithExercises = routinesWithExercises.stream()
                    .filter(routine -> routine.getExercises().stream()
                        .anyMatch(ex -> {
                            // 메인 타겟 또는 서브 타겟에 포함되는지 확인 (완료 여부 무관)
                            ExerciseType exerciseType = ex.getExerciseType();
                            if (exerciseType != null) {
                                boolean matchesMain = exerciseType.getMainTarget() == targetCategory;
                                boolean matchesSub = exerciseType.getSubTargets() != null 
                                    && exerciseType.getSubTargets().contains(targetCategory);
                                return matchesMain || matchesSub;
                            }
                            // exerciseType이 없으면 매칭되지 않음
                            return false;
                        }))
                    .collect(Collectors.toList());
            }
        }
        
        log.info("운동이 있는 루틴 수: {}", routinesWithExercises.size());
        
        return routinesWithExercises.stream()
            .map(routine -> toRoutineResponse(routine, routine.getDate().equals(LocalDate.now())))
            .sorted(Comparator.comparing(RoutineResponse::getDate).reversed())
            .collect(Collectors.toList());
    }
    
    private ExerciseCategory mapBodyPartToCategory(String bodyPart) {
        // BodyPartMapper와 동일한 로직
        switch (bodyPart.trim().toLowerCase()) {
            case "어깨": case "shoulder": return ExerciseCategory.SHOULDER;
            case "가슴": case "chest": return ExerciseCategory.CHEST;
            case "등": case "허리": case "back": return ExerciseCategory.BACK;
            case "팔": case "arm": return ExerciseCategory.ARM;
            case "코어": case "core": return ExerciseCategory.CORE;
            case "복근": case "abs": return ExerciseCategory.ABS;
            case "둔근": case "glute": return ExerciseCategory.GLUTE;
            case "허벅지": case "다리": case "무릎": case "thigh": case "leg": case "knee": return ExerciseCategory.THIGH;
            case "종아리": case "calf": return ExerciseCategory.CALF;
            default: return null;
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public RoutineResponse getRoutineById(Long routineId) {
        Routine routine = routineRepository.findByIdWithExercises(routineId)
            .orElse(null);
        
        if (routine == null) {
            return null;
        }
        
        return toRoutineResponse(routine, routine.getDate().equals(LocalDate.now()));
    }
    
    @Override
    @Transactional(readOnly = true)
    public Map<String, RoutineResponse> getLatestRoutinesByExercise(Long memberId) {
        LocalDate start = LocalDate.now().minusMonths(3);
        LocalDate end = LocalDate.now();
        
        log.info("운동별 최신 루틴 조회: memberId={}, start={}, end={}", memberId, start, end);
        
        // 최근 3개월의 완료된 운동이 있는 루틴 조회
        List<Routine> routines = routineRepository.findByMemberIdAndDateBetween(memberId, start, end);
        
        // 완료된 운동이 있는 루틴만 필터링
        List<Routine> routinesWithCompletedExercises = routines.stream()
            .filter(routine -> routine.getExercises().stream()
                .anyMatch(Exercise::isCompleted))
            .collect(Collectors.toList());
        
        // 운동별로 그룹화하고 각 운동의 가장 최신 루틴만 선택
        Map<String, RoutineResponse> latestByExercise = new HashMap<>();
        
        routinesWithCompletedExercises.forEach(routine -> {
            routine.getExercises().stream()
                .filter(Exercise::isCompleted)
                .forEach(exercise -> {
                    if (exercise.getExerciseType() == null || exercise.getExerciseType().getName() == null) {
                        return; // ExerciseType이 없으면 스킵
                    }
                    String exerciseName = exercise.getExerciseType().getName();
                    
                    // 이미 해당 운동의 루틴이 있고, 현재 루틴이 더 최신이면 업데이트
                    if (!latestByExercise.containsKey(exerciseName) || 
                        routine.getDate().isAfter(latestByExercise.get(exerciseName).getDate())) {
                        // 해당 운동만 포함하는 루틴 응답 생성
                        RoutineResponse response = toRoutineResponseForExercise(routine, exerciseName);
                        if (response != null) {
                            latestByExercise.put(exerciseName, response);
                        }
                    }
                });
        });
        
        log.info("운동별 최신 루틴 조회 결과: exerciseCount={}", latestByExercise.size());
        
        return latestByExercise;
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<RoutineResponse> getRoutinesByExercise(Long memberId, String exerciseName, Pageable pageable) {
        LocalDate start = LocalDate.now().minusMonths(3);
        LocalDate end = LocalDate.now();
        
        log.info("특정 운동의 루틴 조회: memberId={}, exerciseName={}, page={}, size={}", 
            memberId, exerciseName, pageable.getPageNumber(), pageable.getPageSize());
        
        Page<Routine> routines = routineRepository.findByExerciseName(
            memberId, exerciseName, start, end, pageable
        );
        
        // 각 루틴에서 해당 운동만 필터링하여 응답 생성
        List<RoutineResponse> responses = routines.getContent().stream()
            .map(routine -> toRoutineResponseForExercise(routine, exerciseName))
            .filter(response -> response != null)
            .collect(Collectors.toList());
        
        return new PageImpl<>(responses, pageable, routines.getTotalElements());
    }
    
    /**
     * 특정 운동만 포함하는 루틴 응답 생성
     */
    private RoutineResponse toRoutineResponseForExercise(Routine routine, String exerciseName) {
        // 해당 운동만 필터링
        List<ExerciseResponse> exerciseResponses = routine.getExercises().stream()
            .filter(ex -> {
                if (ex.getExerciseType() == null || ex.getExerciseType().getName() == null) {
                    return false;
                }
                String exName = ex.getExerciseType().getName();
                return exName.equals(exerciseName) && ex.isCompleted();
            })
            .sorted(Comparator.comparing(Exercise::getOrderIndex))
            .map(this::toExerciseResponse)
            .collect(Collectors.toList());
        
        if (exerciseResponses.isEmpty()) {
            return null;
        }
        
        return RoutineResponse.builder()
            .id(routine.getId())
            .date(routine.getDate())
            .title(routine.getTitle())
            .status(routine.getStatus().name())
            .isToday(routine.getDate().equals(LocalDate.now()))
            .summary(routine.getAiSummary())
            .exercises(exerciseResponses)
            .build();
    }
    
    @Override
    @Transactional
    public RoutineResponse createRoutine(Long memberId, RoutineCreateRequest request) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다: " + memberId));
        
        // 해당 날짜에 이미 루틴이 있는지 확인
        routineRepository.findByDateAndMemberId(request.getDate(), memberId)
            .ifPresent(routine -> {
                throw new IllegalArgumentException("해당 날짜에 이미 루틴이 존재합니다.");
            });
        
        String title = request.getTitle() != null ? request.getTitle() : "새로운 루틴";
        String summary = request.getSummary() != null && !request.getSummary().isBlank()
            ? request.getSummary()
            : buildCoachingSummary(title, List.of());
        Routine routine = Routine.builder()
            .member(member)
            .date(request.getDate())
            .title(title)
            .aiSummary(summary)
            .status(RoutineStatus.EXPECTED)  // 기본 상태는 EXPECTED (예정)
            .build();
        
        routine = routineRepository.save(routine);
        
        return toRoutineResponse(routine, routine.getDate().equals(LocalDate.now()));
    }
    
    @Override
    @Transactional
    public RoutineResponse updateRoutineStatus(Long routineId, String status) {
        Routine routine = routineRepository.findById(routineId)
            .orElseThrow(() -> new IllegalArgumentException("루틴을 찾을 수 없습니다: " + routineId));
        
        routine.setStatus(RoutineStatus.valueOf(status.toUpperCase()));
        routineRepository.save(routine);
        
        return toRoutineResponse(routine, routine.getDate().equals(LocalDate.now()));
    }
    
    @Override
    @Transactional
    public ExerciseResponse toggleExerciseCompleted(Long routineId, Long exerciseId) {
        Routine routine = routineRepository.findByIdWithExercises(routineId)
            .orElseThrow(() -> new IllegalArgumentException("루틴을 찾을 수 없습니다: " + routineId));
        
        // JOIN FETCH로 이미 로드되었으므로 추가 쿼리 없음
        
        Exercise exercise = routine.getExercises().stream()
            .filter(e -> e.getId().equals(exerciseId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("운동을 찾을 수 없습니다: " + exerciseId));
        
        exercise.setCompleted(!exercise.isCompleted());
        exerciseRepository.save(exercise);
        
        // 운동 완료 상태에 따라 루틴 status 자동 변경
        long completedCount = routine.getExercises().stream()
            .filter(Exercise::isCompleted)
            .count();
        int totalExercises = routine.getExercises().size();
        
        RoutineStatus previousStatus = routine.getStatus();
        RoutineStatus newStatus;
        
        if (completedCount == 0) {
            // 완료된 운동이 하나도 없으면 EXPECTED
            newStatus = RoutineStatus.EXPECTED;
        } else if (completedCount == totalExercises) {
            // 모든 운동이 완료되면 COMPLETED
            newStatus = RoutineStatus.COMPLETED;
        } else {
            // 하나라도 완료되면 IN_PROGRESS
            newStatus = RoutineStatus.IN_PROGRESS;
        }
        
        // 상태가 변경된 경우에만 업데이트
        if (previousStatus != newStatus) {
            routine.setStatus(newStatus);
            routineRepository.save(routine);
            log.info("루틴 상태 자동 변경: routineId={}, previousStatus={}, newStatus={}, completedCount={}, totalExercises={}", 
                routineId, previousStatus, newStatus, completedCount, totalExercises);
        }
        
        // COMPLETED가 되고 오늘 날짜인 경우에만 회고 알림 전송
        if (newStatus == RoutineStatus.COMPLETED && previousStatus != RoutineStatus.COMPLETED) {
            boolean isTodayRoutine = routine.getDate().equals(LocalDate.now());
            if (isTodayRoutine) {
                log.info("오늘의 모든 운동 완료 - 회고 시작: memberId={}, routineId={}", 
                    routine.getMember().getId(), routineId);
                workoutReviewService.startWorkoutReview(routine.getMember().getId());
            } else {
                log.debug("오늘 날짜가 아닌 루틴이므로 회고를 시작하지 않습니다: routineId={}, date={}", 
                    routineId, routine.getDate());
            }
        }
        
        return toExerciseResponse(exercise);
    }
    
    @Override
    @Transactional
    public ExerciseResponse addExercise(Long routineId, ExerciseAddRequest request) {
        Routine routine = routineRepository.findByIdWithExercises(routineId)
            .orElseThrow(() -> new IllegalArgumentException("루틴을 찾을 수 없습니다: " + routineId));

        String name = request.getName();
        if (name != null && !name.isBlank()) {
            boolean alreadyExists = routine.getExercises().stream()
                .anyMatch(e -> e.getExerciseType() != null && name.equals(e.getExerciseType().getName()));
            if (alreadyExists) {
                log.debug("같은 날 루틴에 이미 존재하는 운동은 추가하지 않음: routineId={}, name={}", routineId, name);
                throw new IllegalArgumentException("이 루틴에 이미 같은 운동이 있습니다: " + name);
            }
        }

        // JOIN FETCH로 이미 로드되었으므로 추가 쿼리 없음
        // 다음 orderIndex 계산
        int nextOrderIndex = routine.getExercises().stream()
            .mapToInt(Exercise::getOrderIndex)
            .max()
            .orElse(-1) + 1;

        // ExerciseType 조회 또는 생성
        ExerciseType exerciseType = exerciseTypeRepository.findByName(request.getName())
            .orElseGet(() -> {
                // ExerciseType이 없으면 기본값으로 생성
                ExerciseCategory category = request.getCategory() != null
                    ? ExerciseCategory.valueOf(request.getCategory().toUpperCase())
                    : ExerciseCategory.CHEST; // 기본값
                ExerciseType newType = ExerciseType.builder()
                    .name(request.getName())
                    .mainTarget(category)
                    .subTargets(List.of())
                    .build();
                return exerciseTypeRepository.save(newType);
            });
        
        Exercise exercise = Exercise.builder()
            .exerciseType(exerciseType)
            .sets(request.getSets())
            .reps(request.getReps())
            .weight(request.getWeight())
            .orderIndex(nextOrderIndex)
            .completed(false)
            .routine(routine)
            .build();
        
        exerciseRepository.save(exercise);
        
        return toExerciseResponse(exercise);
    }
    
    @Override
    @Transactional
    public ExerciseResponse updateExercise(Long routineId, Long exerciseId, ExerciseUpdateRequest request) {
        Routine routine = routineRepository.findByIdWithExercises(routineId)
            .orElseThrow(() -> new IllegalArgumentException("루틴을 찾을 수 없습니다: " + routineId));
        
        // JOIN FETCH로 이미 로드되었으므로 추가 쿼리 없음
        Exercise exercise = routine.getExercises().stream()
            .filter(e -> e.getId().equals(exerciseId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("운동을 찾을 수 없습니다: " + exerciseId));
        
        // ExerciseType 업데이트 (이름이 변경된 경우)
        if (exercise.getExerciseType() == null || !exercise.getExerciseType().getName().equals(request.getName())) {
            ExerciseType exerciseType = exerciseTypeRepository.findByName(request.getName())
                .orElseGet(() -> {
                    ExerciseCategory category = request.getCategory() != null 
                        ? ExerciseCategory.valueOf(request.getCategory().toUpperCase())
                        : (exercise.getExerciseType() != null 
                            ? exercise.getExerciseType().getMainTarget() 
                            : ExerciseCategory.CHEST);
                    ExerciseType newType = ExerciseType.builder()
                        .name(request.getName())
                        .mainTarget(category)
                        .subTargets(List.of())
                        .build();
                    return exerciseTypeRepository.save(newType);
                });
            exercise.setExerciseType(exerciseType);
        } else if (request.getCategory() != null) {
            // ExerciseType의 mainTarget 업데이트
            ExerciseCategory category = ExerciseCategory.valueOf(request.getCategory().toUpperCase());
            exercise.getExerciseType().setMainTarget(category);
        }
        if (request.getSets() != null) {
            exercise.setSets(request.getSets());
        }
        if (request.getReps() != null) {
            exercise.setReps(request.getReps());
        }
        if (request.getWeight() != null) {
            exercise.setWeight(request.getWeight());
        }
        
        exerciseRepository.save(exercise);
        
        return toExerciseResponse(exercise);
    }
    
    @Override
    @Transactional
    public void deleteExercise(Long memberId, Long routineId, Long exerciseId) {
        Routine routine = routineRepository.findByIdAndMemberIdWithExercises(routineId, memberId)
            .orElseThrow(() -> new IllegalArgumentException("루틴을 찾을 수 없습니다: " + routineId));

        Exercise exercise = routine.getExercises().stream()
            .filter(e -> e.getId().equals(exerciseId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("운동을 찾을 수 없습니다: " + exerciseId));

        routine.getExercises().remove(exercise);
    }

    @Override
    @Transactional
    public void saveRoutineWithExercisesFromAi(Long memberId, LocalDate date,
                                               RoutineCreateRequest routineCreate,
                                               List<ExerciseAddRequest> exercises) {
        Long routineId;
        Optional<Routine> existingOpt = routineRepository.findByDateAndMemberId(date, memberId);
        if (existingOpt.isPresent()) {
            // 해당 날짜 루틴이 있으면 수정: 기존 운동 제거 후 AI 결과로 갱신
            Routine routine = existingOpt.get();
            routineId = routine.getId();
            routine.getExercises().clear(); // orphanRemoval 로 DB에서 삭제
            if (routineCreate.getTitle() != null) {
                routine.setTitle(routineCreate.getTitle());
            }
            if (routineCreate.getSummary() != null) {
                routine.setAiSummary(routineCreate.getSummary());
            }
            routineRepository.save(routine);
            log.info("[Async] 기존 루틴 수정 후 AI 운동 적용: routineId={}, date={}", routineId, date);
        } else {
            RoutineResponse created = createRoutine(memberId, routineCreate);
            routineId = created.getId();
        }
        List<ExerciseAddRequest> deduped = dedupeExercisesByName(exercises);
        for (ExerciseAddRequest ex : deduped) {
            try {
                addExercise(routineId, ex);
            } catch (IllegalArgumentException e) {
                if (e.getMessage() != null && e.getMessage().contains("이미 같은 운동")) {
                    log.debug("중복 운동 스킵: {}", ex.getName());
                } else {
                    throw e;
                }
            }
        }
        log.info("[Async] AI 루틴 생성/수정 완료: routineId={}, exercises={}", routineId, deduped.size());
        messagingTemplate.convertAndSend("/topic/routine/generate", java.util.Map.of("completed", true, "memberId", memberId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoutinePresetGroupDto> getPresets() {
        return Arrays.asList(
            RoutinePresetGroupDto.builder().groupName("분할 루틴").days(PRESET_GROUP_0).build(),
            RoutinePresetGroupDto.builder().groupName("상하체 루틴").days(PRESET_GROUP_1).build()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public VolumeStatsResponse getVolumeStats(Long memberId, String period) {
        LocalDate today = LocalDate.now();
        boolean isWeek = "week".equalsIgnoreCase(period);

        LocalDate currentStart;
        LocalDate currentEnd;
        LocalDate previousStart;
        LocalDate previousEnd;

        if (isWeek) {
            // 이번 주: 월요일 ~ 일요일
            int dayOfWeek = today.getDayOfWeek().getValue(); // 1=Mon .. 7=Sun
            currentStart = today.minusDays(dayOfWeek - 1);
            currentEnd = currentStart.plusDays(7);
            previousStart = currentStart.minusDays(7);
            previousEnd = currentStart;
        } else {
            // 월별: 이번 달 / 저번 달
            currentStart = today.withDayOfMonth(1);
            currentEnd = currentStart.plusMonths(1);
            previousStart = currentStart.minusMonths(1);
            previousEnd = currentStart;
        }

        List<Routine> currentRoutines = routineRepository.findByMemberIdAndDateBetween(memberId, currentStart, currentEnd.minusDays(1));
        List<Routine> previousRoutines = routineRepository.findByMemberIdAndDateBetween(memberId, previousStart, previousEnd.minusDays(1));

        List<VolumeStatsResponse.VolumeDataPoint> currentPoints = toVolumeDataPoints(currentRoutines);
        List<VolumeStatsResponse.VolumeDataPoint> previousPoints = toVolumeDataPoints(previousRoutines);

        return VolumeStatsResponse.builder()
                .current(currentPoints)
                .previous(previousPoints)
                .build();
    }

    private List<VolumeStatsResponse.VolumeDataPoint> toVolumeDataPoints(List<Routine> routines) {
        return routines.stream()
                .map(r -> {
                    double total = r.getExercises().stream()
                            .filter(Exercise::isCompleted)
                            .mapToDouble(ex -> {
                                int sets = ex.getSets() != null ? ex.getSets() : 0;
                                int reps = ex.getReps() != null ? ex.getReps() : 0;
                                double w = ex.getWeight() != null ? ex.getWeight() : 0.0;
                                return sets * reps * w;
                            })
                            .sum();
                    return VolumeStatsResponse.VolumeDataPoint.builder()
                            .date(r.getDate().toString())
                            .totalVolume(total)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void applyPreset(Long memberId, LocalDate startDate, int presetIndex) {
        List<RoutinePresetDayDto> days = presetIndex == 0 ? PRESET_GROUP_0 : PRESET_GROUP_1;
        for (int i = 0; i < days.size(); i++) {
            RoutinePresetDayDto day = days.get(i);
            LocalDate date = startDate.plusDays(i);
            RoutineCreateRequest routineCreate = new RoutineCreateRequest();
            routineCreate.setDate(date);
            routineCreate.setTitle(day.getTitle());
            routineCreate.setSummary(day.getSummary() != null ? day.getSummary() : (day.getTitle() + " 루틴"));
            List<ExerciseAddRequest> exercises = new ArrayList<>();
            for (String name : day.getExerciseNames()) {
                String category = EXERCISE_NAME_TO_CATEGORY.getOrDefault(name, "CHEST");
                exercises.add(new ExerciseAddRequest(name, category, 3, 10, null));
            }
            saveRoutineWithExercisesFromAi(memberId, date, routineCreate, exercises);
        }
        log.info("프리셋 적용 완료: memberId={}, presetIndex={}, startDate={}, days={}", memberId, presetIndex, startDate, days.size());
        messagingTemplate.convertAndSend("/topic/routine/generate", java.util.Map.of("completed", true, "memberId", memberId));
    }

    @Override
    @Transactional
    public void createRoutinesFromRecommendation(Long memberId, CreateRoutinesFromRecommendationRequest request) {
        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : LocalDate.now();
        List<CreateRoutinesFromRecommendationRequest.DayRecommendation> days = request.getDays();
        if (days == null || days.isEmpty()) {
            return;
        }
        for (CreateRoutinesFromRecommendationRequest.DayRecommendation day : days) {
            int dayIndex = day.getDayIndex() != null ? day.getDayIndex() : 0;
            LocalDate date = startDate.plusDays(dayIndex - 1);
            String title = (day.getLabel() != null && !day.getLabel().isBlank())
                ? day.getLabel().trim()
                : (dayIndex + "일차");
            List<ExerciseAddRequest> exercises = new ArrayList<>();
            if (day.getExercises() != null) {
                for (Map<String, Object> ex : day.getExercises()) {
                    Object nameObj = ex.get("exercise_name") != null ? ex.get("exercise_name") : ex.get("name");
                    Object bodyPartObj = ex.get("body_part");
                    String name = nameObj != null ? String.valueOf(nameObj).trim() : null;
                    if (name == null || name.isEmpty()) continue;
                    String bodyPartStr = bodyPartObj != null ? String.valueOf(bodyPartObj).trim() : null;
                    ExerciseCategory category = bodyPartStr != null ? BodyPartMapper.mapBodyPartToCategory(bodyPartStr) : null;
                    String categoryStr = category != null ? category.name() : "CHEST";
                    exercises.add(new ExerciseAddRequest(name, categoryStr, 3, 10, null));
                }
            }
            RoutineCreateRequest routineCreate = new RoutineCreateRequest();
            routineCreate.setDate(date);
            routineCreate.setTitle(title);
            routineCreate.setSummary(buildCoachingSummary(title, exercises));
            saveRoutineWithExercisesFromAi(memberId, date, routineCreate, exercises);
        }
        log.info("추천 루틴 생성 완료: memberId={}, startDate={}, daysCount={}", memberId, startDate, days.size());
        messagingTemplate.convertAndSend("/topic/routine/generate", java.util.Map.of("completed", true, "memberId", memberId));
    }

    @Override
    @Transactional
    public void swapRoutineDays(Long memberId, LocalDate date1, LocalDate date2) {
        if (date1.equals(date2)) {
            return;
        }
        Optional<Routine> opt1 = routineRepository.findByDateAndMemberId(date1, memberId);
        Optional<Routine> opt2 = routineRepository.findByDateAndMemberId(date2, memberId);
        if (opt1.isEmpty() || opt2.isEmpty()) {
            return;
        }
        Routine routine1 = opt1.get();
        Routine routine2 = opt2.get();
        // 제목·요약 스왑
        String title1 = routine1.getTitle();
        String summary1 = routine1.getAiSummary();
        routine1.setTitle(routine2.getTitle());
        routine1.setAiSummary(routine2.getAiSummary());
        routine2.setTitle(title1);
        routine2.setAiSummary(summary1);
        // 운동 목록 복사본으로 스왑 (엔티티 직접 스왑)
        List<Exercise> list1 = new ArrayList<>(routine1.getExercises());
        List<Exercise> list2 = new ArrayList<>(routine2.getExercises());
        routine1.getExercises().clear();
        routine2.getExercises().clear();
        for (Exercise ex : list2) {
            Exercise copy = Exercise.builder()
                .exerciseType(ex.getExerciseType())
                .sets(ex.getSets())
                .reps(ex.getReps())
                .weight(ex.getWeight())
                .orderIndex(ex.getOrderIndex())
                .completed(ex.isCompleted())
                .routine(routine1)
                .build();
            routine1.getExercises().add(copy);
        }
        for (Exercise ex : list1) {
            Exercise copy = Exercise.builder()
                .exerciseType(ex.getExerciseType())
                .sets(ex.getSets())
                .reps(ex.getReps())
                .weight(ex.getWeight())
                .orderIndex(ex.getOrderIndex())
                .completed(ex.isCompleted())
                .routine(routine2)
                .build();
            routine2.getExercises().add(copy);
        }
        routineRepository.save(routine1);
        routineRepository.save(routine2);
        messagingTemplate.convertAndSend("/topic/routine/generate", java.util.Map.of("completed", true, "memberId", memberId));
        log.info("루틴 요일 맞바꿈 완료: memberId={}, date1={}, date2={}", memberId, date1, date2);
    }

    @Override
    @Transactional
    public void applyPainModify(Long memberId, PainModifyApplyRequest request) {
        if (request.getDate() == null || request.getReplacements() == null) {
            return;
        }
        RoutineResponse routine = getRoutineByDate(memberId, request.getDate());
        if (routine == null || routine.getExercises() == null) {
            return;
        }
        Map<Long, String> replacementMap = request.getReplacements().stream()
            .filter(r -> r.getSelectedName() != null && !r.getSelectedName().isBlank())
            .collect(Collectors.toMap(PainModifyApplyRequest.ReplacementItem::getExerciseId, PainModifyApplyRequest.ReplacementItem::getSelectedName));
        Set<String> seenNames = new LinkedHashSet<>();
        List<ExerciseAddRequest> newExercises = new ArrayList<>();
        for (ExerciseResponse ex : routine.getExercises()) {
            String name = replacementMap.getOrDefault(ex.getId(), ex.getName());
            if (name == null || name.isBlank()) {
                name = ex.getName();
            }
            if (seenNames.contains(name)) {
                name = ex.getName() != null ? ex.getName() : name;
            }
            seenNames.add(name);
            newExercises.add(new ExerciseAddRequest(
                name,
                ex.getMainTarget() != null ? ex.getMainTarget() : "CHEST",
                ex.getSets() != null ? ex.getSets() : 3,
                ex.getReps() != null ? ex.getReps() : 10,
                ex.getWeight()));
        }
        newExercises = dedupeExercisesByName(newExercises);
        RoutineCreateRequest create = new RoutineCreateRequest();
        create.setDate(request.getDate());
        create.setTitle(routine.getTitle());
        create.setSummary(routine.getSummary() != null ? routine.getSummary() : routine.getTitle());
        saveRoutineWithExercisesFromAi(memberId, request.getDate(), create, newExercises);
        log.info("통증 수정 적용 완료: memberId={}, date={}, replacements={}", memberId, request.getDate(), replacementMap.size());
    }

    /**
     * 루틴 제목(날 유형)에 맞는 AI 코칭 요약 생성.
     * 그날 무엇을 조심해야 하고, 어떤 식으로 해야 하는 날인지 팁을 포함.
     */
    private String buildCoachingSummary(String title, List<ExerciseAddRequest> exercises) {
        if (title == null || title.isBlank()) {
            title = "운동";
        }
        String lower = title.toLowerCase();
        StringBuilder sb = new StringBuilder();

        if (lower.contains("chest") || lower.contains("가슴") || lower.contains("triceps") || lower.contains("삼두")) {
            sb.append("어깨·손목 부담이 큰 동작이 있으니 워밍업을 충분히 하세요.\n");
            sb.append("가슴 수축에 집중하고, 무리한 중량보다 동작 품질을 우선하세요.\n");
        } else if (lower.contains("back") || lower.contains("등") || lower.contains("biceps") || lower.contains("이두")) {
            sb.append("등·허리 사용이 많으니 코어를 조이며 자세를 유지하세요.\n");
            sb.append("당기는 동작 시 견갑골을 모으는 느낌으로 하면 효과가 좋아요.\n");
        } else if (lower.contains("shoulder") || lower.contains("어깨")) {
            sb.append("어깨는 부상 위험이 있으니 무게보다 정확한 동작을 우선하세요.\n");
            sb.append("오버헤드 동작 전 어깨·목 스트레칭을 꼭 하세요.\n");
        } else if (lower.contains("leg") || lower.contains("하체") || lower.contains("lower")) {
            sb.append("무릎·허리 과부하를 막으려 워밍업과 동작 범위를 신경 쓰세요.\n");
            sb.append("하체는 부담이 크니 세트 간 휴식과 호흡을 충분히 하세요.\n");
        } else if (lower.contains("arm") || lower.contains("팔")) {
            sb.append("손목·팔꿈치에 무리가 가지 않도록 그립과 각도를 맞추세요.\n");
            sb.append("이두·삼두 수축 구간을 의식하면 자극이 잘 들어가요.\n");
        } else if (lower.contains("upper") || lower.contains("상체")) {
            sb.append("상체 종합이므로 순서대로, 큰 근육(가슴·등)부터 진행하세요.\n");
            sb.append("어깨·손목 워밍업 후 시작하면 부상 위험이 줄어듭니다.\n");
        } else if (lower.contains("core") || lower.contains("복근") || lower.contains("코어")) {
            sb.append("허리 과신전을 피하고, 복부에 힘을 주는 느낌으로 하세요.\n");
            sb.append("호흡을 멈추지 말고, 내쉬는 구간에서 힘을 주세요.\n");
        } else {
            sb.append("워밍업 후 시작하고, 동작이 흐트러지면 무게를 조금 줄여보세요.\n");
        }
        return sb.toString().trim();
    }

    /** 같은 날 루틴에 같은 운동명이 한 번만 들어가도록, 이름 기준 첫 번째만 유지 */
    private List<ExerciseAddRequest> dedupeExercisesByName(List<ExerciseAddRequest> exercises) {
        if (exercises == null || exercises.isEmpty()) {
            return exercises;
        }
        Set<String> seen = new LinkedHashSet<>();
        List<ExerciseAddRequest> result = new ArrayList<>();
        for (ExerciseAddRequest ex : exercises) {
            String name = ex.getName() != null ? ex.getName().trim() : "";
            if (name.isEmpty() || seen.contains(name)) {
                continue;
            }
            seen.add(name);
            result.add(ex);
        }
        return result;
    }

    private RoutineResponse toRoutineResponse(Routine routine, boolean isToday) {
        List<ExerciseResponse> exerciseResponses = routine.getExercises().stream()
            .sorted(Comparator.comparing(Exercise::getOrderIndex))
            .map(this::toExerciseResponse)
            .collect(Collectors.toList());
        
        return RoutineResponse.builder()
            .id(routine.getId())
            .date(routine.getDate())
            .title(routine.getTitle())
            .status(routine.getStatus().name())
            .isToday(isToday)
            .summary(routine.getAiSummary())
            .exercises(exerciseResponses)
            .build();
    }
    
    private ExerciseResponse toExerciseResponse(Exercise exercise) {
        ExerciseType exerciseType = exercise.getExerciseType();
        if (exerciseType == null) {
            throw new IllegalStateException("Exercise must have an ExerciseType");
        }
        // 이름과 타겟 정보는 ExerciseType을 기준으로 사용
        String name = exerciseType.getName();

        ExerciseCategory mainTarget = exerciseType != null 
            ? exerciseType.getMainTarget() 
            : ExerciseCategory.CHEST; // 기본값 (exerciseType이 없는 경우)
        List<String> subTargets = exerciseType != null && exerciseType.getSubTargets() != null
            ? exerciseType.getSubTargets().stream()
                .map(ExerciseCategory::name)
                .collect(Collectors.toList())
            : List.of();
        
        return ExerciseResponse.builder()
            .id(exercise.getId())
            .name(name)
            .mainTarget(mainTarget.name())
            .subTargets(subTargets)
            .sets(exercise.getSets())
            .reps(exercise.getReps())
            .weight(exercise.getWeight())
            .orderIndex(exercise.getOrderIndex())
            .completed(exercise.isCompleted())
            .build();
    }
}

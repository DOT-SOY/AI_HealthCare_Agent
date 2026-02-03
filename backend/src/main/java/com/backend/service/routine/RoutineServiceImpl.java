package com.backend.service.routine;

import com.backend.domain.exercise.Exercise;
import com.backend.domain.exercise.ExerciseCategory;
import com.backend.domain.exercise.ExerciseType;
import com.backend.domain.member.Member;
import com.backend.domain.routine.Routine;
import com.backend.domain.routine.RoutineStatus;
import com.backend.dto.request.ExerciseAddRequest;
import com.backend.dto.request.ExerciseUpdateRequest;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        // 완료된 운동이 있는 루틴만 조회
        List<Routine> routines = routineRepository.findByMemberIdAndDateBetween(
            memberId, 
            LocalDate.now().minusMonths(3), 
            LocalDate.now()
        );
        
        log.info("기록 조회: memberId={}, bodyPart={}, 전체 루틴 수={}", memberId, bodyPart, routines.size());
        
        // JOIN FETCH로 이미 로드되었으므로 추가 쿼리 없음
        // 완료된 운동이 있는 루틴만 필터링
        List<Routine> routinesWithCompletedExercises = routines.stream()
            .filter(routine -> routine.getExercises().stream()
                .anyMatch(Exercise::isCompleted))
            .collect(Collectors.toList());
        
        // bodyPart 필터링 (메인 타겟 또는 서브 타겟 포함)
        if (bodyPart != null && !bodyPart.isEmpty() && !bodyPart.equals("전체")) {
            ExerciseCategory targetCategory = mapBodyPartToCategory(bodyPart);
            if (targetCategory != null) {
                routinesWithCompletedExercises = routinesWithCompletedExercises.stream()
                    .filter(routine -> routine.getExercises().stream()
                        .anyMatch(ex -> {
                            if (!ex.isCompleted()) return false;
                            // 메인 타겟 또는 서브 타겟에 포함되는지 확인
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
        
        log.info("완료된 운동이 있는 루틴 수: {}", routinesWithCompletedExercises.size());
        
        return routinesWithCompletedExercises.stream()
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
        
        Routine routine = Routine.builder()
            .member(member)
            .date(request.getDate())
            .title(request.getTitle() != null ? request.getTitle() : "새로운 루틴")
            .aiSummary(request.getSummary() != null ? request.getSummary() : "")
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
    public void deleteExercise(Long routineId, Long exerciseId) {
        Routine routine = routineRepository.findByIdWithExercises(routineId)
            .orElseThrow(() -> new IllegalArgumentException("루틴을 찾을 수 없습니다: " + routineId));
        
        // JOIN FETCH로 이미 로드되었으므로 추가 쿼리 없음
        Exercise exercise = routine.getExercises().stream()
            .filter(e -> e.getId().equals(exerciseId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("운동을 찾을 수 없습니다: " + exerciseId));
        
        // 컬렉션에서 제거 → Routine.orphanRemoval=true 로 DB에서도 삭제됨
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
        for (ExerciseAddRequest ex : exercises) {
            addExercise(routineId, ex);
        }
        log.info("[Async] AI 루틴 생성/수정 완료: routineId={}, exercises={}", routineId, exercises.size());
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

    @Override
    @Transactional(readOnly = true)
    public VolumeStatsResponse getVolumeStats(Long memberId, String period) {
        log.info("총 볼륨 통계 조회: memberId={}, period={}", memberId, period);

        LocalDate today = LocalDate.now();
        LocalDate startDate, endDate, previousStartDate, previousEndDate;

        if ("week".equals(period)) {
            // 주별: 이번 주와 저번 주
            int dayOfWeek = today.getDayOfWeek().getValue() - 1; // 0(월) ~ 6(일)
            startDate = today.minusDays(dayOfWeek); // 이번 주 월요일
            endDate = startDate.plusDays(6); // 이번 주 일요일
            previousStartDate = startDate.minusWeeks(1); // 저번 주 월요일
            previousEndDate = previousStartDate.plusDays(6); // 저번 주 일요일
        } else {
            // 월별: 이번 달과 저번 달
            startDate = LocalDate.of(today.getYear(), today.getMonth(), 1); // 이번 달 1일
            endDate = startDate.plusMonths(1).minusDays(1); // 이번 달 마지막 날
            previousStartDate = startDate.minusMonths(1); // 저번 달 1일
            previousEndDate = startDate.minusDays(1); // 저번 달 마지막 날
        }

        // 이번 기간 루틴 조회
        List<Routine> currentRoutines = routineRepository.findByMemberIdAndDateBetween(
            memberId, startDate, endDate
        );

        // 저번 기간 루틴 조회
        List<Routine> previousRoutines = routineRepository.findByMemberIdAndDateBetween(
            memberId, previousStartDate, previousEndDate
        );

        // 총 볼륨 계산
        List<VolumeStatsResponse.VolumeDataPoint> currentData = calculateVolumeDataPoints(currentRoutines);
        List<VolumeStatsResponse.VolumeDataPoint> previousData = calculateVolumeDataPoints(previousRoutines);

        return VolumeStatsResponse.builder()
            .current(currentData)
            .previous(previousData)
            .build();
    }

    private List<VolumeStatsResponse.VolumeDataPoint> calculateVolumeDataPoints(List<Routine> routines) {
        Map<LocalDate, Double> volumeByDate = new HashMap<>();

        for (Routine routine : routines) {
            double totalVolume = 0.0;

            // 완료된 운동만 포함하여 총 볼륨 계산
            for (Exercise exercise : routine.getExercises()) {
                if (exercise.isCompleted() && exercise.getSets() != null &&
                    exercise.getReps() != null && exercise.getWeight() != null) {
                    totalVolume += exercise.getSets() * exercise.getReps() * exercise.getWeight();
                }
            }

            if (totalVolume > 0) {
                volumeByDate.put(routine.getDate(), totalVolume);
            }
        }

        // 날짜순으로 정렬하여 반환
        return volumeByDate.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> VolumeStatsResponse.VolumeDataPoint.builder()
                .date(entry.getKey().toString())
                .totalVolume(entry.getValue())
                .build())
            .collect(Collectors.toList());
    }
}

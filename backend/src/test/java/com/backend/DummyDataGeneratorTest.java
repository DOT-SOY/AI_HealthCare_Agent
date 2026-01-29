package com.backend;

import com.backend.domain.exercise.Exercise;
import com.backend.domain.exercise.ExerciseCategory;
import com.backend.domain.exercise.ExerciseType;
import com.backend.domain.member.Member;
import com.backend.domain.routine.Routine;
import com.backend.domain.routine.RoutineStatus;
import com.backend.dto.member.MemberDTO;
import com.backend.repository.exercise.ExerciseRepository;
import com.backend.repository.exercise.ExerciseTypeRepository;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.routine.RoutineRepository;
import com.backend.service.member.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 더미데이터 생성 테스트
 * 
 * 사용 방법:
 * 1. IDE에서 이 테스트를 실행
 * 2. 또는 Gradle로 실행: ./gradlew test --tests "com.backend.DummyDataGeneratorTest.generateDummyData"
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("더미데이터 생성")
class DummyDataGeneratorTest {
    
    @Autowired
    private MemberRepository memberRepository;
    
    @Autowired
    private MemberService memberService;
    
    @Autowired
    private RoutineRepository routineRepository;
    
    @Autowired
    private ExerciseRepository exerciseRepository;
    
    @Autowired
    private ExerciseTypeRepository exerciseTypeRepository;
    
    @Test
    @Transactional
    @Rollback(false) // 테스트 후 롤백하지 않음 (DB에 실제로 저장)
    @DisplayName("최근 1주일간의 루틴과 운동 기록 생성")
    void generateDummyData() {
        System.out.println("=== 더미데이터 생성 시작 ===");
        
        // 1. 더미 데이터용 회원 1명을 이메일로 고정 생성/재사용
        // MemberService.join()을 사용하여 비밀번호가 해싱된 회원 생성
        String dummyEmail = "dummy@example.com";
        Member member = memberRepository.findByEmail(dummyEmail)
            .orElseGet(() -> {
                // MemberDTO 생성 (비밀번호는 validation 규칙에 맞게 설정)
                MemberDTO memberDTO = MemberDTO.builder()
                    .email(dummyEmail)
                    .pw("Test1234!") // 8자 이상, 영문+숫자+특수문자 조합 (validation 통과)
                    .name("테스트회원") // 한글 2자 이상
                    .gender("MALE")
                    .birthDate("1990-01-01") // YYYY-MM-DD 형식
                    .height(175)
                    .weight(70.0)
                    .build();
                
                // MemberService.join()을 통해 비밀번호 해싱 후 저장
                Long memberId = memberService.join(memberDTO);
                Member saved = memberRepository.findById(memberId)
                    .orElseThrow(() -> new RuntimeException("회원 생성 실패"));
                System.out.println("새 멤버 생성: ID=" + saved.getId() + ", 이메일=" + saved.getEmail());
                System.out.println("비밀번호: Test1234! (해싱되어 저장됨)");
                return saved;
            });
        
        System.out.println("=== 사용할 멤버 정보 ===");
        System.out.println("멤버 ID: " + member.getId());
        System.out.println("멤버 이메일: " + member.getEmail());
        System.out.println("멤버 이름: " + member.getName());
        System.out.println("=========================");
        
        // 2. ExerciseType이 없으면 생성 (초기화)
        ensureExerciseTypesExist();
        
        // 3. 1일부터 25일까지의 루틴 생성
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.withDayOfMonth(1); // 이번 달 1일
        LocalDate endDate = today.withDayOfMonth(25); // 이번 달 25일
        
        // 25일이 오늘보다 미래면 오늘까지만
        if (endDate.isAfter(today)) {
            endDate = today;
        }
        
        List<Routine> routines = new ArrayList<>();
        int dayCount = 0;
        
        // 1일부터 25일까지 루틴 생성
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            dayCount++;
            
            // 기존 루틴이 있으면 삭제 후 재생성
            routineRepository.findByDateAndMemberId(date, member.getId())
                .ifPresent(routine -> {
                    exerciseRepository.deleteAll(routine.getExercises());
                    routineRepository.delete(routine);
                });
            
            Routine routine = Routine.builder()
                .member(member)
                .date(date)
                .title("HYPERTROPHY WORKOUT DAY " + dayCount)
                .status(RoutineStatus.COMPLETED) // 25일까지 모두 완료
                .aiSummary("오늘의 운동 루틴입니다.")
                .build();
            
            routines.add(routine);
        }
        
        // 4. 루틴 저장
        routines = routineRepository.saveAll(routines);
        System.out.println("루틴 생성 완료: " + routines.size() + "개 (1일 ~ " + endDate.getDayOfMonth() + "일)");
        
        // 5. 각 루틴에 운동 추가 (9개 운동 모두 포함, 중복 가능)
        int totalExercises = 0;
        String[] allExerciseNames = {
            "데드리프트", "벤치프레스", "오버헤드프레스", "바벨 컬", 
            "플랭크", "행잉레그레이즈", "힙쓰러스트", "스쿼트", "카프레이즈"
        };
        
        for (int i = 0; i < routines.size(); i++) {
            Routine routine = routines.get(i);
            List<Exercise> exercises = new ArrayList<>();
            int orderIndex = 0;
            
            // 각 루틴에 9개 운동 모두 추가 (같은 날 중복 가능)
            // 랜덤하게 3-6개 운동 선택하여 추가
            int exerciseCount = 3 + (i % 4); // 3, 4, 5, 6개 랜덤
            
            for (int j = 0; j < exerciseCount; j++) {
                String exerciseName = allExerciseNames[(i * 3 + j) % allExerciseNames.length];
                ExerciseType exerciseType = exerciseTypeRepository.findByName(exerciseName)
                    .orElseThrow(() -> new RuntimeException("ExerciseType을 찾을 수 없습니다: " + exerciseName));
                
                // 무게는 운동에 따라 다르게 설정
                double weight = getWeightForExercise(exerciseName, i);
                int sets = 3 + (i % 2); // 3 또는 4세트
                int reps = 8 + (i % 5); // 8~12회
                
                exercises.add(createExercise(routine, exerciseName, exerciseType, sets, reps, weight, orderIndex++, true)); // 모두 완료
            }
            
            exerciseRepository.saveAll(exercises);
            totalExercises += exercises.size();
        }
        
        System.out.println("운동 생성 완료: " + totalExercises + "개");
        System.out.println("=== 더미데이터 생성 완료 ===");
        System.out.println("생성된 루틴 수: " + routines.size());
        System.out.println("생성된 운동 수: " + totalExercises);
    }
    
    /**
     * ExerciseType이 존재하는지 확인하고 없으면 생성
     */
    private void ensureExerciseTypesExist() {
        if (exerciseTypeRepository.count() == 0) {
            System.out.println("ExerciseType이 없어서 생성합니다...");
            
            // 9개 운동 타입 생성
            createExerciseType("데드리프트", ExerciseCategory.BACK, 
                List.of(ExerciseCategory.GLUTE, ExerciseCategory.THIGH));
            createExerciseType("벤치프레스", ExerciseCategory.CHEST, 
                List.of(ExerciseCategory.ARM, ExerciseCategory.SHOULDER));
            createExerciseType("오버헤드프레스", ExerciseCategory.SHOULDER, 
                List.of(ExerciseCategory.ARM, ExerciseCategory.CORE));
            createExerciseType("바벨 컬", ExerciseCategory.ARM, 
                List.of(ExerciseCategory.SHOULDER, ExerciseCategory.CORE));
            createExerciseType("플랭크", ExerciseCategory.CORE, 
                List.of(ExerciseCategory.SHOULDER, ExerciseCategory.GLUTE));
            createExerciseType("행잉레그레이즈", ExerciseCategory.ABS, 
                List.of(ExerciseCategory.SHOULDER));
            createExerciseType("힙쓰러스트", ExerciseCategory.GLUTE, 
                List.of(ExerciseCategory.THIGH));
            createExerciseType("스쿼트", ExerciseCategory.THIGH, 
                List.of(ExerciseCategory.GLUTE));
            createExerciseType("카프레이즈", ExerciseCategory.CALF, 
                List.of(ExerciseCategory.THIGH, ExerciseCategory.CORE));
            
            System.out.println("ExerciseType 생성 완료: " + exerciseTypeRepository.count() + "개");
        } else {
            System.out.println("ExerciseType 이미 존재: " + exerciseTypeRepository.count() + "개");
        }
    }
    
    private void createExerciseType(String name, ExerciseCategory mainTarget, List<ExerciseCategory> subTargets) {
        if (exerciseTypeRepository.findByName(name).isEmpty()) {
            ExerciseType exerciseType = ExerciseType.builder()
                .name(name)
                .mainTarget(mainTarget)
                .subTargets(subTargets)
                .build();
            exerciseTypeRepository.save(exerciseType);
        }
    }
    
    /**
     * 운동별 무게 설정
     */
    private double getWeightForExercise(String exerciseName, int dayIndex) {
        switch (exerciseName) {
            case "데드리프트":
                return 120.0 + (dayIndex * 2);
            case "벤치프레스":
                return 80.0 + (dayIndex * 1.5);
            case "오버헤드프레스":
                return 50.0 + (dayIndex * 1);
            case "바벨 컬":
                return 25.0 + (dayIndex * 0.5);
            case "플랭크":
            case "행잉레그레이즈":
            case "카프레이즈":
                return 0.0; // 체중 운동
            case "힙쓰러스트":
                return 80.0 + (dayIndex * 2);
            case "스쿼트":
                return 100.0 + (dayIndex * 2.5);
            default:
                return 50.0;
        }
    }
    
    private Exercise createExercise(Routine routine, String name, ExerciseType exerciseType, 
                                   int sets, int reps, double weight, int orderIndex, boolean completed) {
        return Exercise.builder()
            .routine(routine)
            .name(name)
            .exerciseType(exerciseType)
            .sets(sets)
            .reps(reps)
            .weight(weight)
            .completed(completed)
            .orderIndex(orderIndex)
            .build();
    }
}


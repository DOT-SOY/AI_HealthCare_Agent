package com.backend.config;

import com.backend.domain.exercise.ExerciseCategory;
import com.backend.domain.exercise.ExerciseType;
import com.backend.repository.exercise.ExerciseTypeRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExerciseTypeInitializer {
    
    private final ExerciseTypeRepository exerciseTypeRepository;
    
    @PostConstruct
    @Transactional
    public void initializeExerciseTypes() {
        log.info("ExerciseType 초기 데이터 생성 시작...");
        
        // 이미 존재하는 운동 타입 확인
        List<String> existingNames = exerciseTypeRepository.findAll().stream()
            .map(et -> et.getName())
            .collect(Collectors.toList());
        
        // 데이터가 없으면 전체 초기화
        if (existingNames.isEmpty()) {
            log.info("ExerciseType 데이터가 없습니다. 전체 초기화를 진행합니다.");
        
        // 1. 등 / 데드리프트 - 서브 타겟 : 둔근 / 허벅지
        createExerciseType("데드리프트", ExerciseCategory.BACK, 
            Arrays.asList(ExerciseCategory.GLUTE, ExerciseCategory.THIGH));
        
        // 2. 가슴 / 벤치프레스 - 서브 타겟 : 팔 / 어깨
        createExerciseType("벤치프레스", ExerciseCategory.CHEST, 
            Arrays.asList(ExerciseCategory.ARM, ExerciseCategory.SHOULDER));
        
        // 3. 어깨 / 오버헤드프레스 - 서브 타겟 : 팔 / 코어
        createExerciseType("오버헤드프레스", ExerciseCategory.SHOULDER, 
            Arrays.asList(ExerciseCategory.ARM, ExerciseCategory.CORE));
        
        // 4. 팔 / 바벨 컬 - 서브 타겟 : 어깨 / 코어
        createExerciseType("바벨 컬", ExerciseCategory.ARM, 
            Arrays.asList(ExerciseCategory.SHOULDER, ExerciseCategory.CORE));
        
        // 5. 코어 / 플랭크 - 서브 타겟 : 어깨, 둔근
        createExerciseType("플랭크", ExerciseCategory.CORE, 
            Arrays.asList(ExerciseCategory.SHOULDER, ExerciseCategory.GLUTE));
        
        // 6. 복근 / 행잉레그레이즈 - 서브 타겟 : 어깨
        createExerciseType("행잉레그레이즈", ExerciseCategory.ABS, 
            Arrays.asList(ExerciseCategory.SHOULDER));
        
        // 7. 둔근 / 힙쓰러스트 - 서브 타겟 : 허벅지
        createExerciseType("힙쓰러스트", ExerciseCategory.GLUTE, 
            Arrays.asList(ExerciseCategory.THIGH));
        
        // 8. 허벅지 / 스쿼트 - 서브 타겟 : 둔근
        createExerciseType("스쿼트", ExerciseCategory.THIGH, 
            Arrays.asList(ExerciseCategory.GLUTE));
        
        // 9. 종아리 / 카프레이즈 - 서브 타겟 : 허벅지 / 코어
        createExerciseType("카프레이즈", ExerciseCategory.CALF, 
            Arrays.asList(ExerciseCategory.THIGH, ExerciseCategory.CORE));
        
        // 10. 등 / 턱걸이 - 서브 타겟 : 팔 / 어깨 / 코어
        createExerciseType("턱걸이", ExerciseCategory.BACK, 
            Arrays.asList(ExerciseCategory.ARM, ExerciseCategory.SHOULDER, ExerciseCategory.CORE));
        
        // 11. 복근 / 윗몸일으키기 - 서브 타겟 : 코어
        createExerciseType("윗몸일으키기", ExerciseCategory.ABS, 
            Arrays.asList(ExerciseCategory.CORE));
        
        } else {
            // 일부 운동 타입만 추가 (누락된 것만)
            log.info("ExerciseType 데이터가 이미 존재합니다. 누락된 운동 타입만 추가합니다.");
            
            // 턱걸이 추가
            if (!existingNames.contains("턱걸이")) {
                createExerciseType("턱걸이", ExerciseCategory.BACK, 
                    Arrays.asList(ExerciseCategory.ARM, ExerciseCategory.SHOULDER, ExerciseCategory.CORE));
            }
            
            // 윗몸일으키기 추가
            if (!existingNames.contains("윗몸일으키기")) {
                createExerciseType("윗몸일으키기", ExerciseCategory.ABS, 
                    Arrays.asList(ExerciseCategory.CORE));
            }
        }
        
        log.info("ExerciseType 초기 데이터 생성 완료: {}개", exerciseTypeRepository.count());
    }
    
    private void createExerciseType(String name, ExerciseCategory mainTarget, List<ExerciseCategory> subTargets) {
        ExerciseType exerciseType = ExerciseType.builder()
            .name(name)
            .mainTarget(mainTarget)
            .subTargets(subTargets)
            .build();
        exerciseTypeRepository.save(exerciseType);
        log.info("ExerciseType 생성: {} (메인: {}, 서브: {})", name, mainTarget, subTargets);
    }
}



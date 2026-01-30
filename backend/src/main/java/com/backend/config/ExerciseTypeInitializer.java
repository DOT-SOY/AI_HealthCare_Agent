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

@Component
@RequiredArgsConstructor
@Slf4j
public class ExerciseTypeInitializer {
    
    private final ExerciseTypeRepository exerciseTypeRepository;
    
    @PostConstruct
    @Transactional
    public void initializeExerciseTypes() {
        // 이미 데이터가 있으면 초기화하지 않음
        if (exerciseTypeRepository.count() > 0) {
            log.info("ExerciseType 데이터가 이미 존재합니다. 초기화를 건너뜁니다.");
            return;
        }
        
        log.info("ExerciseType 초기 데이터 생성 시작...");
        
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



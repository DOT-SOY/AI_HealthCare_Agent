package com.backend.util;

import com.backend.domain.exercise.ExerciseCategory;

import java.util.HashMap;
import java.util.Map;

public class BodyPartMapper {
    
    private static final Map<String, ExerciseCategory> BODY_PART_TO_CATEGORY = new HashMap<>();
    
    static {
        // 한글 통증 부위 -> ExerciseCategory 매핑
        BODY_PART_TO_CATEGORY.put("어깨", ExerciseCategory.SHOULDER);
        BODY_PART_TO_CATEGORY.put("가슴", ExerciseCategory.CHEST);
        BODY_PART_TO_CATEGORY.put("등", ExerciseCategory.BACK);
        BODY_PART_TO_CATEGORY.put("허리", ExerciseCategory.BACK);
        BODY_PART_TO_CATEGORY.put("다리", ExerciseCategory.LEG);
        BODY_PART_TO_CATEGORY.put("무릎", ExerciseCategory.LEG);
        BODY_PART_TO_CATEGORY.put("팔", ExerciseCategory.ARM);
        BODY_PART_TO_CATEGORY.put("복근", ExerciseCategory.CORE);
        BODY_PART_TO_CATEGORY.put("코어", ExerciseCategory.CORE);
        
        // 영문 통증 부위 -> ExerciseCategory 매핑
        BODY_PART_TO_CATEGORY.put("shoulder", ExerciseCategory.SHOULDER);
        BODY_PART_TO_CATEGORY.put("chest", ExerciseCategory.CHEST);
        BODY_PART_TO_CATEGORY.put("back", ExerciseCategory.BACK);
        BODY_PART_TO_CATEGORY.put("leg", ExerciseCategory.LEG);
        BODY_PART_TO_CATEGORY.put("knee", ExerciseCategory.LEG);
        BODY_PART_TO_CATEGORY.put("arm", ExerciseCategory.ARM);
        BODY_PART_TO_CATEGORY.put("core", ExerciseCategory.CORE);
    }
    
    /**
     * 통증 부위 문자열을 ExerciseCategory로 변환합니다.
     * 
     * @param bodyPart 통증 부위 (한글 또는 영문)
     * @return ExerciseCategory, 매핑되지 않으면 null
     */
    public static ExerciseCategory mapBodyPartToCategory(String bodyPart) {
        if (bodyPart == null) {
            return null;
        }
        
        String normalized = bodyPart.trim().toLowerCase();
        
        // 한글 매핑 확인
        ExerciseCategory category = BODY_PART_TO_CATEGORY.get(bodyPart.trim());
        if (category != null) {
            return category;
        }
        
        // 영문 소문자 매핑 확인
        return BODY_PART_TO_CATEGORY.get(normalized);
    }
}

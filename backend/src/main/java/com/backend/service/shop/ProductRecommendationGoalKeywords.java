package com.backend.service.shop;

import com.backend.domain.memberinfo.MemberInfoBody.ExercisePurpose;

import java.util.Collections;
import java.util.List;

public final class ProductRecommendationGoalKeywords {

    private ProductRecommendationGoalKeywords() {
    }

    private static final List<String> EXCLUDE_FOR_BULK_UP = List.of(
            "다이어트", "저칼로리", "클린", "체중감량", "살빼기", "유지"
    );

    private static final List<String> EXCLUDE_FOR_DIET = List.of(
            "벌크업", "게이너", "증량", "근육증가", "유지"
    );

    private static final List<String> EXCLUDE_FOR_MAINTAIN = List.of(
            "다이어트", "살빼기", "벌크업", "게이너", "증량", "체중감량"
    );

    public static List<String> getExcludeKeywords(ExercisePurpose goal) {
        if (goal == null) {
            return Collections.emptyList();
        }
        return switch (goal) {
            case BULK_UP -> EXCLUDE_FOR_BULK_UP;
            case DIET -> EXCLUDE_FOR_DIET;
            case MAINTAIN -> EXCLUDE_FOR_MAINTAIN;
        };
    }
}

package com.backend.dto.shop.request;

import com.backend.domain.memberinfo.MemberInfoBody.ExercisePurpose;
import com.backend.domain.shop.CategoryType;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 상품 추천 요청 DTO
 */
@Getter
@Setter
public class ProductRecommendationRequest {
    
    // 운동 목적
    private ExercisePurpose goal;
    
    // 상품 카테고리
    private CategoryType productCategory;
    
    // 예산 상한
    @Min(0)
    private BigDecimal budgetMax;
    
    // 회피 성분/알러지
    private List<String> avoid;
    
    // 필수 포함 성분
    private List<String> mustHave;
    
    // 우선순위 조건
    private List<String> priority;

    /** 상품명/검색 키워드 (optional, null/빈 값이면 키워드 검색 없음) */
    private String keyword;

    /** 검색 대상: name(상품명), description(상품내용), all(전체). null이면 "all" */
    private String searchType;
}


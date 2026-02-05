package com.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 상품 추천 관련 설정.
 * application.properties에서 recommendation.* 프리픽스로 설정 가능.
 */
@Configuration
@ConfigurationProperties(prefix = "recommendation")
@Getter
@Setter
public class RecommendationConfig {

    /** candidate_pool 상한 (DB 1차 필터 후 최대 가져올 개수) */
    private int candidatePoolLimit = 500;

    /** 인기 버킷 크기 (Bucket A) */
    private int bucketPopularSize = 30;

    /** 신상품 버킷 크기 (Bucket B) */
    private int bucketNewSize = 20;

    /** 최종 후보 목표 개수 (Top 50) */
    private int topCandidatesTarget = 50;

    /** 최종 추천 노출 개수 */
    private int finalCount = 3;

    /** 인기 그룹 경계 (popularity_norm 차이가 이 값 이하면 같은 그룹) - 넓게 설정하면 더 공격적으로 keywordScore 재정렬 */
    private double popularityGroupThreshold = 0.15;
    
    /** 후보 수가 이 값 이하면 keywordScore 우선 정렬로 전환 */
    private int smallCandidateThreshold = 5;
    
    /** popularity 보유 상품 비율이 이 값 미만이면 keywordScore 우선 정렬로 전환 (0.0 ~ 1.0) */
    private double minPopularityRatioForRanking = 0.3;

    /** 필터를 강하게 적용할 최소 filteredPool 크기 (이 이상이면 goal/avoid/mustHave를 하드 필터로 사용) */
    private int minFilteredPoolSizeForStrictFilter = 10;

    /** 1차 categoryId 기반 candidatePool이 이 값 미만이면 category를 제거한 relaxed 검색을 한 번 더 수행 */
    private int minCandidatePoolSizeForRelaxedCategory = 5;

    // === Goal 패널티 설정 (하드 필터 대신 점수 차감으로 사용) ===
    
    /** goal 제외 키워드가 상품명에 포함될 때 적용할 패널티 (음수 권장, 예: -35) */
    private int goalPenaltyForName = -35;

    /** goal 제외 키워드가 설명에만 포함될 때 적용할 패널티 (음수 권장, 예: -20) */
    private int goalPenaltyForDescription = -20;
}

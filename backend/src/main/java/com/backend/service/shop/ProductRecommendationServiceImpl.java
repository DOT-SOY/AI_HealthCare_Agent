package com.backend.service.shop;

import com.backend.common.dto.PageRequest;
import com.backend.domain.shop.Category;
import com.backend.domain.shop.CategoryType;
import com.backend.domain.shop.Product;
import com.backend.domain.shop.ProductStatus;
import com.backend.domain.shop.ProductVariant;
import com.backend.dto.shop.request.ProductRecommendationRequest;
import com.backend.dto.shop.response.ProductRecommendationItem;
import com.backend.dto.shop.response.ProductRecommendationResponse;
import com.backend.repository.shop.*;
import com.backend.config.RecommendationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductRecommendationServiceImpl implements ProductRecommendationService {
    
    private final ProductSearch productSearch;
    private final CategoryRepository categoryRepository;
    private final ProductSalesRankingService productSalesRankingService;
    private final ProductVariantRepository productVariantRepository;
    private final RecommendationConfig recommendationConfig;
    
    @Override
    public ProductRecommendationResponse recommend(ProductRecommendationRequest request) {
        String searchType = (request.getSearchType() != null && !request.getSearchType().isBlank())
                ? request.getSearchType().trim() : "all";
        log.info("상품 추천 요청: goal={}, category={}, budgetMax={}, keyword={}, avoid={}, mustHave={}, priority={}",
                request.getGoal(), request.getProductCategory(), request.getBudgetMax(),
                request.getKeyword(), request.getAvoid(), request.getMustHave(), request.getPriority());

        // 1. 카테고리 ID 조회
        Long categoryId = null;
        if (request.getProductCategory() != null && request.getProductCategory() != CategoryType.ETC) {
            categoryId = categoryRepository.findByCategoryTypeAndParentIsNull(request.getProductCategory())
                    .map(Category::getId)
                    .orElse(null);
        }

        // 2. 인기 상품 ID 리스트 조회 (Redis ZSET)
        List<Long> popularityIds = null;
        if (categoryId != null) {
            popularityIds = productSalesRankingService.getTopProductIdsByCategory(categoryId, 300);
        }

        // 3. DB 검색 조건 구성
        // [개선] goal 기반 excludeNameKeywords는 더 이상 하드 필터로 사용하지 않음
        // → 점수 패널티(calculateKeywordScores)에서 처리
        ProductSearchCondition condition = ProductSearchCondition.builder()
                .categoryId(categoryId)
                .maxPrice(request.getBudgetMax())
                .status(ProductStatus.ACTIVE)
                .excludeOutOfStock(true)
                .direction("DESC")
                .keyword(request.getKeyword() != null && !request.getKeyword().isBlank() ? request.getKeyword().trim() : null)
                .searchType(searchType)
                .excludeNameKeywords(null)  // goal 기반 키워드 제외 비활성화
                .sortBy("createdAt")
                .build();

        // 4. DB 1차 필터로 candidate_pool 생성 (최대 설정값 개)
        PageRequest pageRequest = new PageRequest();
        pageRequest.setPage(1);
        pageRequest.setPageSize(recommendationConfig.getCandidatePoolLimit());

        Page<Product> candidatePoolPage = productSearch.search(condition, pageRequest.toPageable());
        // 항상 가변 리스트로 복사 (relaxed 검색 병합 시 add 가능)
        List<Product> candidatePool = new ArrayList<>(candidatePoolPage.getContent());
        int originalCandidateSize = candidatePool.size();
        
        log.info("DB 1차 필터 결과 (candidatePool): {} 상품, keyword=\"{}\", categoryId={}",
                originalCandidateSize, condition.getKeyword(), categoryId);
        // 상위 5개 상품 로그
        for (int i = 0; i < Math.min(5, candidatePool.size()); i++) {
            Product p = candidatePool.get(i);
            log.info("  candidatePool[{}] id={} name=\"{}\"", i, p.getId(), p.getName());
        }

        // 4-1. 1차 candidate_pool이 너무 적을 경우 categoryId 없이 한 번 더 검색하여 후보 풀 확장
        int minCategoryRelax = recommendationConfig.getMinCandidatePoolSizeForRelaxedCategory();
        if (condition.getCategoryId() != null && candidatePool.size() > 0 && candidatePool.size() < minCategoryRelax) {
            log.info("candidatePool이 작아 category 없는 relaxed 검색 수행: size={}, minRelax={}, categoryId={}",
                    candidatePool.size(), minCategoryRelax, condition.getCategoryId());

            ProductSearchCondition relaxedCondition = ProductSearchCondition.builder()
                    .categoryId(null)
                    .minPrice(condition.getMinPrice())
                    .maxPrice(condition.getMaxPrice())
                    .status(condition.getStatus())
                    .sortBy(condition.getSortBy())
                    .direction(condition.getDirection())
                    .excludeOutOfStock(condition.isExcludeOutOfStock())
                    .keyword(condition.getKeyword())
                    .searchType(condition.getSearchType())
                    .excludeNameKeywords(null)  // goal 기반 키워드 제외 비활성화
                    .build();

            Page<Product> relaxedPage = productSearch.search(relaxedCondition, pageRequest.toPageable());
            List<Product> relaxedCandidates = relaxedPage.getContent();

            if (!relaxedCandidates.isEmpty()) {
                // 1차 결과를 우선 유지하고, category 없는 relaxed 결과를 뒤에 병합 (중복 제거)
                Set<Long> seenIds = candidatePool.stream()
                        .map(Product::getId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                for (Product p : relaxedCandidates) {
                    if (seenIds.add(p.getId())) {
                        candidatePool.add(p);
                    }
                }
                int relaxedAdded = candidatePool.size() - originalCandidateSize;
                log.info("relaxed 검색 병합 결과: original={}, relaxedAdded={}, merged={}",
                        originalCandidateSize, relaxedAdded, candidatePool.size());
            } else {
                log.info("relaxed 검색 결과 없음 - 1차 candidatePool만 사용");
            }
        }

        // 5. keyword 검색 0건 시 fallback 처리
        if (candidatePool.isEmpty() && condition.getKeyword() != null) {
            ProductSearchCondition fallbackCondition = ProductSearchCondition.builder()
                    .categoryId(categoryId)
                    .maxPrice(request.getBudgetMax())
                    .status(ProductStatus.ACTIVE)
                    .excludeOutOfStock(true)
                    .sortBy("createdAt")
                    .direction("DESC")
                    .keyword(null)
                    .searchType(searchType)
                    .excludeNameKeywords(null)  // goal 기반 키워드 제외 비활성화
                    .build();
            PageRequest fallbackPageRequest = new PageRequest();
            fallbackPageRequest.setPage(1);
            fallbackPageRequest.setPageSize(50);
            Page<Product> fallbackPage = productSearch.search(fallbackCondition, fallbackPageRequest.toPageable());
            List<Product> fallbackProducts = fallbackPage.getContent();
            List<ProductRecommendationItem> alternativeItems = buildRecommendationItems(
                    fallbackProducts.stream().limit(recommendationConfig.getFinalCount()).collect(Collectors.toList()),
                    productVariantRepository);
            return ProductRecommendationResponse.builder()
                    .products(alternativeItems)
                    .conditionMatched(false)
                    .alternativeCandidates(List.of())
                    .build();
        }

        if (candidatePool.isEmpty()) {
            return ProductRecommendationResponse.builder()
                    .products(List.of())
                    .conditionMatched(null)
                    .build();
        }

        // 6. Goal 기반 필터링 비활성화 
        // [개선] goal 기반 제외 키워드는 더 이상 하드 필터로 사용하지 않음
        // → 점수 패널티(calculateKeywordScores)에서 처리하여 순위만 뒤로 밀림
        List<Product> goalFiltered = candidatePool;
        // goal 하드 필터 제거됨 - 모든 상품 유지

        // 7. Avoid 키워드 필터링
        List<Product> filteredPool = goalFiltered;
        if (request.getAvoid() != null && !request.getAvoid().isEmpty()) {
            filteredPool = goalFiltered.stream()
                    .filter(product -> {
                        String name = product.getName() != null ? product.getName().toLowerCase() : "";
                        String description = product.getDescription() != null ? product.getDescription().toLowerCase() : "";
                        return request.getAvoid().stream()
                                .noneMatch(avoidKeyword -> {
                                    if (avoidKeyword == null || avoidKeyword.isBlank()) return false;
                                    String ak = avoidKeyword.toLowerCase();
                                    return name.contains(ak) || description.contains(ak);
                                });
                    })
                    .collect(Collectors.toList());
            log.info("상품 추천 avoid 필터 적용 전/후: before={}, after={}, avoid={}",
                    goalFiltered.size(), filteredPool.size(), request.getAvoid());
        }

        // 7-1. mustHave 키워드 소프트 필터링 (부위 키워드 제외, 상품 유형만 최소 1개 이상 매칭 시 통과)
        // 부위 키워드는 점수 보정에서만 사용하고, hard 필터에는 상품 유형 키워드만 적용
        List<Product> mustHaveFiltered = filteredPool;
        if (request.getMustHave() != null && !request.getMustHave().isEmpty()) {
            // 부위 vs 상품 유형 분리
            MustHaveSplit split = splitMustHaveIntoBodyAndType(request.getMustHave());
            List<String> typeKeywords = split.typeKeywords;
            
            log.info("mustHave 분리 결과: bodyParts={}, typeKeywords={}", split.bodyParts, typeKeywords);
            
            if (!typeKeywords.isEmpty()) {
                // 소프트 필터: 상품 유형 키워드 중 최소 1개 이상 포함하면 통과 (OR 조건)
                mustHaveFiltered = filteredPool.stream()
                        .filter(product -> {
                            String name = product.getName() != null ? product.getName().toLowerCase() : "";
                            String description = product.getDescription() != null ? product.getDescription().toLowerCase() : "";
                            String combined = name + " " + description;
                            // 하나라도 포함되면 통과 (OR 조건)
                            return typeKeywords.stream().anyMatch(combined::contains);
                        })
                        .collect(Collectors.toList());
                
                log.info("상품 추천 mustHave 소프트 필터 적용 전/후: before={}, after={}, typeKeywords={}",
                        filteredPool.size(), mustHaveFiltered.size(), typeKeywords);
                
                // 필터링 후 0건이면 원본 pool 유지 (완전 필터링 방지)
                if (mustHaveFiltered.isEmpty()) {
                    log.warn("mustHave 소프트 필터 결과 0건 - 원본 pool 유지: typeKeywords={}", typeKeywords);
                    mustHaveFiltered = filteredPool;
                }
            } else {
                log.info("상품 유형 키워드 없음 - mustHave 필터 스킵 (부위만 있음: {})", split.bodyParts);
            }
        }
        filteredPool = mustHaveFiltered;
        
        // 디버깅: filteredPool 상위 10개 상품 로그
        log.info("filteredPool 상위 상품 (최대 10개): total={}", filteredPool.size());
        for (int i = 0; i < Math.min(10, filteredPool.size()); i++) {
            Product p = filteredPool.get(i);
            log.info("  [{}] id={} name=\"{}\"", i + 1, p.getId(), p.getName());
        }

        // 7-2. 후보 수 부족 시 필터를 완화하고 candidatePool 기반으로 스코어링
        int strictThreshold = recommendationConfig.getMinFilteredPoolSizeForStrictFilter();
        List<Product> poolForScoring;
        if (filteredPool.size() >= strictThreshold) {
            poolForScoring = filteredPool;
            log.info("poolForScoring=filteredPool 사용: filteredPoolSize={}, strictThreshold={}",
                    filteredPool.size(), strictThreshold);
        } else {
            poolForScoring = candidatePool;
            log.info("poolForScoring=candidatePool 사용 (필터 완화): filteredPoolSize={}, candidatePoolSize={}, strictThreshold={}",
                    filteredPool.size(), candidatePool.size(), strictThreshold);
        }

        // 8. 인기 ZSET 기반 popularity rank map 구성
        final Map<Long, Integer> popularityRank = buildPopularityRankMap(popularityIds);
        log.info("인기 랭킹 맵 구성: popularityIds={}", popularityIds != null ? popularityIds.size() : 0);

        // 9. 2버킷 전략으로 Top 50 구성
        List<Product> top50Candidates = buildTop50WithTwoBuckets(poolForScoring, popularityRank, request);
        
        if (top50Candidates.isEmpty()) {
            return ProductRecommendationResponse.builder()
                    .products(List.of())
                    .conditionMatched(null)
                    .build();
        }

        // 10. 키워드 기반 추가 점수 계산 (semantic 대체 역할)
        final Set<String> requestBodyTokens = extractBodyTokens(request);
        List<ProductWithScore> scoredProducts = calculateKeywordScores(top50Candidates, request, popularityRank, requestBodyTokens);

        // 11. popularity 우선 정렬 + 유사 인기 그룹 내 semantic(keyword score) 재정렬
        List<Product> finalSorted = sortWithPopularityFirstAndLocalReorder(scoredProducts, popularityRank);

        // 12. 상위 N개 추출 및 응답 생성
        List<Product> topProducts = finalSorted.stream()
                .limit(recommendationConfig.getFinalCount())
                .collect(Collectors.toList());

        // 디버깅용 로그
        logFinalRecommendations(topProducts, scoredProducts, request);

        List<ProductRecommendationItem> items = buildRecommendationItems(topProducts, productVariantRepository);
        
        return ProductRecommendationResponse.builder()
                .products(items)
                .conditionMatched(true)
                .build();
    }

    /**
     * 2버킷 전략으로 최종 후보 구성.
     * - Bucket A (인기 버킷): poolForScoring ∩ popularity ZSET에서 상위 K개
     * - Bucket B (신상품 버킷): poolForScoring에서 createdAt DESC 상위 M개
     * - A+B merge 후 중복 제거, 목표 개수가 안 되면 남은 pool에서 보충
     */
    private List<Product> buildTop50WithTwoBuckets(
            List<Product> poolForScoring, 
            Map<Long, Integer> popularityRank,
            ProductRecommendationRequest request) {
        
        Set<Long> selectedIds = new LinkedHashSet<>();
        List<Product> result = new ArrayList<>();

        int bucketPopularSize = recommendationConfig.getBucketPopularSize();
        int bucketNewSize = recommendationConfig.getBucketNewSize();
        int topCandidatesTarget = recommendationConfig.getTopCandidatesTarget();

        log.info("=== 2버킷 구성 시작 (poolForScoring 기반) ===");
        log.info("설정: bucketPopularSize={}, bucketNewSize={}, topCandidatesTarget={}, poolSize={}", 
                bucketPopularSize, bucketNewSize, topCandidatesTarget, poolForScoring.size());

        // Bucket A: 인기 버킷
        List<Product> bucketA = new ArrayList<>();
        if (popularityRank != null && !popularityRank.isEmpty()) {
            bucketA = poolForScoring.stream()
                    .filter(p -> popularityRank.containsKey(p.getId()))
                    .sorted(Comparator.comparingInt(p -> popularityRank.getOrDefault(p.getId(), Integer.MAX_VALUE)))
                    .limit(bucketPopularSize)
                    .collect(Collectors.toList());
            
            log.info("Bucket A (인기 버킷): {} 상품 선택됨 (poolForScoring 중 인기 랭킹 보유: {})",
                    bucketA.size(),
                    poolForScoring.stream().filter(p -> popularityRank.containsKey(p.getId())).count());
            for (int i = 0; i < bucketA.size(); i++) {
                Product p = bucketA.get(i);
                int rank = popularityRank.getOrDefault(p.getId(), -1);
                log.info("  BucketA[{}] id={} name=\"{}\" popularityRank={}", 
                        i, p.getId(), p.getName(), rank);
            }
        } else {
            log.info("Bucket A (인기 버킷): 인기 랭킹 데이터 없음 - 스킵");
        }

        // Bucket B: 신상품 버킷 (createdAt DESC)
        List<Product> bucketB = poolForScoring.stream()
                .sorted(Comparator.comparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(bucketNewSize)
                .collect(Collectors.toList());
        
        log.info("Bucket B (신상품 버킷): {} 상품 선택됨", bucketB.size());
        for (int i = 0; i < bucketB.size(); i++) {
            Product p = bucketB.get(i);
            log.info("  BucketB[{}] id={} name=\"{}\" createdAt={}", 
                    i, p.getId(), p.getName(), p.getCreatedAt());
        }

        // A+B merge (중복 제거)
        for (Product p : bucketA) {
            if (selectedIds.add(p.getId())) {
                result.add(p);
            }
        }
        int afterBucketA = result.size();
        
        for (Product p : bucketB) {
            if (selectedIds.add(p.getId())) {
                result.add(p);
            }
        }
        int afterBucketB = result.size();
        int duplicateCount = (bucketA.size() + bucketB.size()) - afterBucketB;

        log.info("A+B 병합: A에서 {} + B에서 {} = {} (중복 제거: {})", 
                afterBucketA, afterBucketB - afterBucketA, afterBucketB, duplicateCount);

        // 목표 개수가 안 되면 보충
        if (result.size() < topCandidatesTarget) {
            int beforeFill = result.size();
            for (Product p : poolForScoring) {
                if (result.size() >= topCandidatesTarget) break;
                if (selectedIds.add(p.getId())) {
                    result.add(p);
                }
            }
            log.info("보충: {} -> {} ({}개 추가)", beforeFill, result.size(), result.size() - beforeFill);
        }

        log.info("=== 2버킷 구성 완료: total={} ===", result.size());

        return result;
    }

    /**
     * popularity 순위 맵 구성.
     */
    private Map<Long, Integer> buildPopularityRankMap(List<Long> popularityIds) {
        if (popularityIds == null || popularityIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Integer> rankMap = new HashMap<>();
        for (int i = 0; i < popularityIds.size(); i++) {
            rankMap.put(popularityIds.get(i), i);
        }
        return rankMap;
    }

    /**
     * mustHave 키워드를 부위 토큰과 상품 유형 키워드로 분리.
     * 부위 토큰은 점수 보정에만 사용하고, 상품 유형만 필터/우선순위에 적용.
     */
    private MustHaveSplit splitMustHaveIntoBodyAndType(List<String> mustHaveList) {
        Set<String> bodyParts = new HashSet<>();
        List<String> typeKeywords = new ArrayList<>();
        
        // 부위 후보 (extractBodyTokens와 동일)
        Set<String> bodyTokenCandidates = Set.of("무릎", "하체", "허벅지", "손목", "손", "허리", "등", "어깨", "발목", "팔꿈치", "상체", "팔");
        
        // 상품 유형 후보 (카탈로그에서 실제 검색 가능한 키워드)
        Set<String> typeTokenCandidates = Set.of(
                "보호대", "니슬리브", "니랩", "스트랩", "밴드", "벨트", "슬리브",
                "덤벨", "바벨", "케틀벨", "기구", "매트", "폼롤러", "풀업바",
                "보충제", "프로틴", "단백질", "게이너", "아미노산", "크레아틴", "bcaa",
                "음식", "식품", "국수", "닭가슴살", "샐러드", "간식",
                "레깅스", "운동복", "반바지", "티셔츠"
        );
        
        for (String kw : mustHaveList) {
            if (kw == null || kw.isBlank()) continue;
            String lower = kw.toLowerCase().trim();
            
            boolean isBody = false;
            for (String body : bodyTokenCandidates) {
                if (lower.contains(body)) {
                    bodyParts.add(body);
                    isBody = true;
                }
            }
            
            // 상품 유형 키워드인지 확인
            boolean isType = false;
            for (String type : typeTokenCandidates) {
                if (lower.contains(type)) {
                    isType = true;
                    break;
                }
            }
            
            // 상품 유형 키워드이거나, 부위가 아니면 typeKeywords에 추가
            // (부위와 상품 유형이 같이 들어있으면 상품 유형 우선)
            if (isType || !isBody) {
                typeKeywords.add(lower);
            }
        }
        
        return new MustHaveSplit(bodyParts, typeKeywords);
    }
    
    /**
     * mustHave 분리 결과 (부위 vs 상품 유형).
     */
    private static class MustHaveSplit {
        final Set<String> bodyParts;
        final List<String> typeKeywords;
        
        MustHaveSplit(Set<String> bodyParts, List<String> typeKeywords) {
            this.bodyParts = bodyParts;
            this.typeKeywords = typeKeywords;
        }
    }

    /**
     * 요청에서 부위 키워드 추출 (keyword와 priority에서만).
     * mustHave의 부위는 splitMustHaveIntoBodyAndType에서 별도 처리됨.
     */
    private Set<String> extractBodyTokens(ProductRecommendationRequest request) {
        Set<String> bodyTokens = new HashSet<>();
        // splitMustHaveIntoBodyAndType와 동일한 부위 후보 사용
        Set<String> bodyTokenCandidates = Set.of("무릎", "하체", "허벅지", "손목", "손", "허리", "등", "어깨", "발목", "팔꿈치", "상체", "팔");
        
        // keyword에서 부위 추출
        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            String lowerKw = request.getKeyword().toLowerCase();
            for (String body : bodyTokenCandidates) {
                if (lowerKw.contains(body)) bodyTokens.add(body);
            }
        }
        
        // priority에서 부위 추출
        if (request.getPriority() != null) {
            for (String priority : request.getPriority()) {
                if (priority == null || priority.isBlank()) continue;
                String lower = priority.toLowerCase();
                for (String body : bodyTokenCandidates) {
                    if (lower.contains(body)) bodyTokens.add(body);
                }
            }
        }
        
        return bodyTokens;
    }

    /**
     * 키워드 기반 점수 계산 (semantic score 대체).
     * - 상품 유형 매칭: 높은 가중치 (+25 이름, +15 설명)
     * - 부위 매칭: 중간 가중치 (+30 이름, +20 설명)
     * - priority/keyword: 보조 가중치
     * - goal 패널티: goal과 상충하는 키워드 포함 시 점수 차감
     */
    private List<ProductWithScore> calculateKeywordScores(
            List<Product> candidates,
            ProductRecommendationRequest request,
            Map<Long, Integer> popularityRank,
            Set<String> requestBodyTokens) {
        
        // mustHave를 부위/유형으로 분리
        MustHaveSplit mustHaveSplit = request.getMustHave() != null 
                ? splitMustHaveIntoBodyAndType(request.getMustHave()) 
                : new MustHaveSplit(Set.of(), List.of());
        
        // goal 기반 제외 키워드 (패널티 적용용)
        List<String> goalExcludeKeywords = request.getGoal() != null
                ? ProductRecommendationGoalKeywords.getExcludeKeywords(request.getGoal())
                : List.of();
        
        return candidates.stream()
                .map(product -> {
                    int score = 0;
                    int goalPenalty = 0;
                    String name = product.getName() != null ? product.getName().toLowerCase() : "";
                    String description = product.getDescription() != null ? product.getDescription().toLowerCase() : "";

                    // 상품 유형 키워드 점수 (높은 가중치)
                    for (String typeKw : mustHaveSplit.typeKeywords) {
                        if (name.contains(typeKw)) score += 25;  // 이름 매칭 우선
                        else if (description.contains(typeKw)) score += 15;  // 설명 매칭
                    }

                    // 부위 키워드 점수 (requestBodyTokens + mustHaveSplit.bodyParts 모두 반영)
                    Set<String> allBodyTokens = new HashSet<>(requestBodyTokens);
                    allBodyTokens.addAll(mustHaveSplit.bodyParts);
                    for (String bodyToken : allBodyTokens) {
                        if (name.contains(bodyToken)) {
                            score += 30;  // 이름에 부위 직접 포함 시 높은 점수
                        } else if (description.contains(bodyToken)) {
                            score += 20;  // 설명에 부위 포함 시 보조 점수
                        }
                    }

                    // priority 점수 (영양/성분 등 soft preference)
                    if (request.getPriority() != null) {
                        for (String priority : request.getPriority()) {
                            if (priority == null || priority.isBlank()) continue;
                            String pk = priority.toLowerCase().replace("_", " ").replace("낮음", "").replace("높음", "").trim();
                            if (!pk.isEmpty()) {
                                if (name.contains(pk)) score += 8;
                                if (description.contains(pk)) score += 5;
                            }
                        }
                    }

                    // keyword 점수 (DB 검색 키워드와의 매칭)
                    if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
                        String[] tokens = request.getKeyword().toLowerCase().trim().split("\\s+");
                        for (String token : tokens) {
                            if (token.length() < 2) continue;
                            if (name.contains(token)) score += 6;
                            if (description.contains(token)) score += 4;
                        }
                    }

                    // [개선] goal 패널티 적용 (하드 필터 대신 점수 차감)
                    // - goal과 상충하는 키워드가 상품명에 포함되면 더 큰 패널티
                    // - 설명에만 포함되면 작은 패널티
                    for (String excludeKw : goalExcludeKeywords) {
                        if (excludeKw == null || excludeKw.isBlank()) continue;
                        String lowerExclude = excludeKw.toLowerCase();
                        if (name.contains(lowerExclude)) {
                            goalPenalty += recommendationConfig.getGoalPenaltyForName();  // 상품명에 포함 시 큰 패널티
                        } else if (description.contains(lowerExclude)) {
                            goalPenalty += recommendationConfig.getGoalPenaltyForDescription();  // 설명에만 포함 시 작은 패널티
                        }
                    }

                    int finalScore = score + goalPenalty;
                    return new ProductWithScore(product, finalScore);
                })
                .collect(Collectors.toList());
    }

    /**
     * popularity 우선 정렬 + 유사 인기 그룹 내 keyword score 재정렬.
     * 
     * [개선] 후보가 적거나 popularity 데이터가 부족할 때는 keywordScore 우선 정렬로 전환.
     * 
     * 1. 후보가 적으면(≤5) 또는 popularity 보유 상품 비율이 낮으면 → keywordScore 우선 정렬
     * 2. 그 외: popularity DESC → 그룹 내 keywordScore 재정렬
     */
    private List<Product> sortWithPopularityFirstAndLocalReorder(
            List<ProductWithScore> scoredProducts,
            Map<Long, Integer> popularityRank) {
        
        if (scoredProducts.isEmpty()) return List.of();

        List<ProductWithScore> sorted = new ArrayList<>(scoredProducts);
        
        // popularity 보유 상품 수 계산
        long popularityCount = sorted.stream()
                .filter(pws -> popularityRank.containsKey(pws.product.getId()))
                .count();
        double popularityRatio = (double) popularityCount / sorted.size();
        
        // [조건 1] 후보 수가 적거나 (≤5) popularity 데이터가 부족하면 (보유 비율 < 30%) 
        // → keywordScore 우선 정렬로 전환
        int smallCandidateThreshold = recommendationConfig.getSmallCandidateThreshold();
        double minPopularityRatio = recommendationConfig.getMinPopularityRatioForRanking();
        
        if (sorted.size() <= smallCandidateThreshold || popularityRatio < minPopularityRatio) {
            log.info("keywordScore 우선 정렬 적용: candidates={}, popularityCount={}, popularityRatio={}",
                    sorted.size(), popularityCount, String.format("%.2f", popularityRatio));
            // keywordScore DESC → createdAt DESC 정렬
            sorted.sort((a, b) -> {
                if (a.score != b.score) return Integer.compare(b.score, a.score);
                // tie-break: createdAt DESC
                Instant createdA = a.product.getCreatedAt();
                Instant createdB = b.product.getCreatedAt();
                if (createdA == null && createdB == null) return 0;
                if (createdA == null) return 1;
                if (createdB == null) return -1;
                return createdB.compareTo(createdA);
            });
            return sorted.stream().map(pws -> pws.product).collect(Collectors.toList());
        }

        // [조건 2] 그 외: popularity 우선 + 그룹 내 keywordScore 재정렬
        // 1차 정렬: popularity DESC (rank가 작을수록 인기), tie-break: createdAt DESC
        sorted.sort((a, b) -> {
            int rankA = popularityRank.getOrDefault(a.product.getId(), Integer.MAX_VALUE);
            int rankB = popularityRank.getOrDefault(b.product.getId(), Integer.MAX_VALUE);
            if (rankA != rankB) return Integer.compare(rankA, rankB);
            // createdAt DESC
            Instant createdA = a.product.getCreatedAt();
            Instant createdB = b.product.getCreatedAt();
            if (createdA == null && createdB == null) return 0;
            if (createdA == null) return 1;
            if (createdB == null) return -1;
            return createdB.compareTo(createdA);
        });

        // 로컬 popularity_norm 계산
        int minRank = Integer.MAX_VALUE;
        int maxRank = Integer.MIN_VALUE;
        for (ProductWithScore pws : sorted) {
            int rank = popularityRank.getOrDefault(pws.product.getId(), Integer.MAX_VALUE);
            if (rank != Integer.MAX_VALUE) {
                minRank = Math.min(minRank, rank);
                maxRank = Math.max(maxRank, rank);
            }
        }
        
        // 인기 상품이 없는 경우 (모두 MAX_VALUE) 그냥 score 기준 정렬
        if (minRank == Integer.MAX_VALUE) {
            sorted.sort((a, b) -> Integer.compare(b.score, a.score));
            return sorted.stream().map(pws -> pws.product).collect(Collectors.toList());
        }

        final int finalMinRank = minRank;
        final int finalMaxRank = maxRank;
        
        // popularity_norm 계산 함수 (0~1, 인기 높을수록 1에 가까움)
        java.util.function.ToDoubleFunction<ProductWithScore> popNorm = pws -> {
            int rank = popularityRank.getOrDefault(pws.product.getId(), Integer.MAX_VALUE);
            if (rank == Integer.MAX_VALUE) return 0.0;
            if (finalMaxRank == finalMinRank) return 1.0;
            return (double)(finalMaxRank - rank) / (finalMaxRank - finalMinRank);
        };

        // 그룹 단위 재정렬 (넓은 threshold 사용으로 더 공격적으로 재정렬)
        List<Product> result = new ArrayList<>();
        int i = 0;
        while (i < sorted.size()) {
            List<ProductWithScore> group = new ArrayList<>();
            group.add(sorted.get(i));
            double basePop = popNorm.applyAsDouble(sorted.get(i));
            
            // 인접 상품 중 popularity_norm 차이가 threshold 이하인 것들을 같은 그룹에 포함
            int j = i + 1;
            while (j < sorted.size()) {
                double nextPop = popNorm.applyAsDouble(sorted.get(j));
                if (Math.abs(basePop - nextPop) <= recommendationConfig.getPopularityGroupThreshold()) {
                    group.add(sorted.get(j));
                    basePop = nextPop;
                    j++;
                } else {
                    break;
                }
            }
            
            // 그룹 내 keyword score로 재정렬
            if (group.size() > 1) {
                group.sort((a, b) -> Integer.compare(b.score, a.score));
            }
            
            for (ProductWithScore pws : group) {
                result.add(pws.product);
            }
            
            i = j;
        }
        
        return result;
    }

    /**
     * 최종 추천 결과 로깅.
     */
    private void logFinalRecommendations(
            List<Product> topProducts,
            List<ProductWithScore> allScored,
            ProductRecommendationRequest request) {
        
        if (topProducts.isEmpty()) return;
        
        Map<Long, Integer> scoreMap = allScored.stream()
                .collect(Collectors.toMap(pws -> pws.product.getId(), pws -> pws.score, (a, b) -> a));
        
        log.info("최종 추천 결과 ({}개) - keyword={}, mustHave={}, avoid={}",
                topProducts.size(), request.getKeyword(), request.getMustHave(), request.getAvoid());
        for (int i = 0; i < topProducts.size(); i++) {
            Product p = topProducts.get(i);
            int score = scoreMap.getOrDefault(p.getId(), 0);
            log.info("  rank={} productId={} name=\"{}\" keywordScore={}",
                    i + 1, p.getId(), p.getName(), score);
        }
    }

    private List<ProductRecommendationItem> buildRecommendationItems(
            List<Product> productList,
            ProductVariantRepository variantRepo) {
        if (productList == null || productList.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = productList.stream().map(Product::getId).collect(Collectors.toList());
        List<ProductVariant> allVariants = variantRepo.findByProductIdIn(productIds);
        Map<Long, List<ProductVariant>> variantsByProductId = allVariants.stream()
                .collect(Collectors.groupingBy(v -> v.getProduct().getId()));
        return productList.stream()
                .map(product -> {
                    List<ProductVariant> availableVariants = variantsByProductId
                            .getOrDefault(product.getId(), List.of())
                            .stream()
                            .filter(v -> v.isActive() && v.getStockQty() > 0)
                            .collect(Collectors.toList());
                    List<ProductRecommendationItem.ProductVariantSummary> variantSummaries =
                            availableVariants.stream()
                                    .map(v -> ProductRecommendationItem.ProductVariantSummary.builder()
                                            .variantId(v.getId())
                                            .name(v.getOptionText())
                                            .stockQty(v.getStockQty())
                                            .build())
                                    .collect(Collectors.toList());
                    BigDecimal price = product.getBasePrice();
                    if (!availableVariants.isEmpty() && availableVariants.get(0).getPrice() != null) {
                        price = availableVariants.get(0).getPrice();
                    }
                    return ProductRecommendationItem.builder()
                            .productId(product.getId())
                            .name(product.getName())
                            .price(price)
                            .thumbnailUrl(null)
                            .availableVariants(variantSummaries)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private static class ProductWithScore {
        final Product product;
        final int score;
        
        ProductWithScore(Product product, int score) {
            this.product = product;
            this.score = score;
        }
    }
}


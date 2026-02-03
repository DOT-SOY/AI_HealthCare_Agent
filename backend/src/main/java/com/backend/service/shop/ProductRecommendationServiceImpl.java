package com.backend.service.shop;

import com.backend.common.dto.PageRequest;
import com.backend.domain.shop.*;
import com.backend.dto.shop.request.ProductRecommendationRequest;
import com.backend.dto.shop.response.ProductRecommendationItem;
import com.backend.dto.shop.response.ProductRecommendationResponse;
import com.backend.repository.shop.*;
import com.backend.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 상품 추천 서비스 구현
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductRecommendationServiceImpl implements ProductRecommendationService {
    
    private final ProductSearch productSearch;
    private final CategoryRepository categoryRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductVariantRepository productVariantRepository;
    private final FileStorageService fileStorageService;
    
    @Override
    public ProductRecommendationResponse recommend(ProductRecommendationRequest request) {
        String searchType = (request.getSearchType() != null && !request.getSearchType().isBlank())
                ? request.getSearchType().trim() : "all";
        log.info("상품 추천 요청: goal={}, category={}, budgetMax={}, keyword={}, avoid={}, mustHave={}, priority={}",
                request.getGoal(), request.getProductCategory(), request.getBudgetMax(),
                request.getKeyword(), request.getAvoid(), request.getMustHave(), request.getPriority());

        // 1. 카테고리 ID 조회 (productCategory가 있는 경우)
        Long categoryId = null;
        if (request.getProductCategory() != null && request.getProductCategory() != CategoryType.ETC) {
            categoryId = categoryRepository.findByCategoryTypeAndParentIsNull(request.getProductCategory())
                    .map(Category::getId)
                    .orElse(null);
        }

        // 2. ProductSearchCondition 생성 (하드 필터 + keyword optional)
        ProductSearchCondition condition = ProductSearchCondition.builder()
                .categoryId(categoryId)
                .maxPrice(request.getBudgetMax())
                .status(ProductStatus.ACTIVE)  // 판매중만
                .excludeOutOfStock(true)  // 재고>0만
                .sortBy("createdAt")
                .direction("DESC")
                .keyword(request.getKeyword() != null && !request.getKeyword().isBlank() ? request.getKeyword().trim() : null)
                .searchType(searchType)
                .build();

        // 3. 기본 검색 (하드 필터 적용)
        PageRequest pageRequest = new PageRequest();
        pageRequest.setPage(1);
        pageRequest.setPageSize(50);  // 충분히 많이 가져와서 소프트 필터링 및 정렬 수행

        Page<Product> products = productSearch.search(condition, pageRequest.toPageable());
        List<Product> candidateProducts = products.getContent();

        // 0건이고 keyword가 있었으면 keyword 없이 재검색 (fallback)
        if (candidateProducts.isEmpty() && condition.getKeyword() != null) {
            ProductSearchCondition fallbackCondition = ProductSearchCondition.builder()
                    .categoryId(categoryId)
                    .maxPrice(request.getBudgetMax())
                    .status(ProductStatus.ACTIVE)
                    .excludeOutOfStock(true)
                    .sortBy("createdAt")
                    .direction("DESC")
                    .keyword(null)
                    .searchType(searchType)
                    .build();
            products = productSearch.search(fallbackCondition, pageRequest.toPageable());
            candidateProducts = products.getContent();
        }

        if (candidateProducts.isEmpty()) {
            return ProductRecommendationResponse.builder()
                    .products(List.of())
                    .build();
        }
        
        // 4. 소프트 필터링: avoid 키워드 필터링 (description에서 검색)
        List<Product> filteredProducts = candidateProducts;
        if (request.getAvoid() != null && !request.getAvoid().isEmpty()) {
            filteredProducts = candidateProducts.stream()
                    .filter(product -> {
                        String description = product.getDescription() != null 
                                ? product.getDescription().toLowerCase() 
                                : "";
                        // avoid 키워드가 description에 포함되지 않은 상품만 통과
                        return request.getAvoid().stream()
                                .noneMatch(avoidKeyword -> 
                                        description.contains(avoidKeyword.toLowerCase()));
                    })
                    .collect(Collectors.toList());
        }
        
        // 5. 가중치 정렬 (must_have, priority 반영)
        List<ProductWithScore> scoredProducts = filteredProducts.stream()
                .map(product -> {
                    int score = 0;
                    String description = product.getDescription() != null 
                            ? product.getDescription().toLowerCase() 
                            : "";
                    
                    // must_have 키워드 매칭 시 +10점
                    if (request.getMustHave() != null && !request.getMustHave().isEmpty()) {
                        for (String mustHave : request.getMustHave()) {
                            if (description.contains(mustHave.toLowerCase())) {
                                score += 10;
                            }
                        }
                    }
                    
                    // priority 조건 매칭 시 +5점
                    if (request.getPriority() != null && !request.getPriority().isEmpty()) {
                        for (String priority : request.getPriority()) {
                            // 간단한 키워드 매칭 (예: "칼로리_낮음" -> "칼로리" 검색)
                            String priorityKeyword = priority.toLowerCase()
                                    .replace("_", " ")
                                    .replace("낮음", "")
                                    .replace("높음", "")
                                    .trim();
                            if (description.contains(priorityKeyword)) {
                                score += 5;
                            }
                        }
                    }
                    
                    return new ProductWithScore(product, score);
                })
                .sorted((a, b) -> Integer.compare(b.score, a.score))  // 점수 내림차순
                .collect(Collectors.toList());
        
        // 6. Top 3 선택
        List<Product> topProducts = scoredProducts.stream()
                .limit(3)
                .map(pws -> pws.product)
                .collect(Collectors.toList());
        
        // 7. 이미지 및 변형 조회
        List<Long> productIds = topProducts.stream().map(Product::getId).collect(Collectors.toList());
        List<ProductImage> allImages = productImageRepository.findByProductIdIn(productIds);
        List<ProductVariant> allVariants = productVariantRepository.findByProductIdIn(productIds);
        
        Map<Long, List<ProductImage>> imagesByProductId = allImages.stream()
                .collect(Collectors.groupingBy(img -> img.getProduct().getId()));
        Map<Long, List<ProductVariant>> variantsByProductId = allVariants.stream()
                .collect(Collectors.groupingBy(v -> v.getProduct().getId()));
        
        // 8. 응답 DTO 변환
        List<ProductRecommendationItem> items = topProducts.stream()
                .map(product -> {
                    // Primary 이미지 찾기
                    String thumbnailUrl = null;
                    List<ProductImage> images = imagesByProductId.getOrDefault(product.getId(), List.of());
                    Optional<ProductImage> primaryImage = images.stream()
                            .filter(ProductImage::isPrimaryImage)
                            .findFirst();
                    if (primaryImage.isPresent()) {
                        thumbnailUrl = fileStorageService.getFileUrl(primaryImage.get().getFilePath());
                    } else if (!images.isEmpty()) {
                        thumbnailUrl = fileStorageService.getFileUrl(images.get(0).getFilePath());
                    }
                    
                    // 사용 가능한 변형 목록 (재고>0, active=true)
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
                    
                    // 가격 결정 (variant가 있으면 첫 번째 variant 가격, 없으면 basePrice)
                    BigDecimal price = product.getBasePrice();
                    if (!availableVariants.isEmpty() && availableVariants.get(0).getPrice() != null) {
                        price = availableVariants.get(0).getPrice();
                    }
                    
                    return ProductRecommendationItem.builder()
                            .productId(product.getId())
                            .name(product.getName())
                            .price(price)
                            .thumbnailUrl(thumbnailUrl)
                            .availableVariants(variantSummaries)
                            .build();
                })
                .collect(Collectors.toList());
        
        return ProductRecommendationResponse.builder()
                .products(items)
                .build();
    }
    
    /**
     * 상품과 점수를 함께 저장하는 내부 클래스
     */
    private static class ProductWithScore {
        final Product product;
        final int score;
        
        ProductWithScore(Product product, int score) {
            this.product = product;
            this.score = score;
        }
    }
}


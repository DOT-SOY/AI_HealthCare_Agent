package com.backend.controller.shop;

import com.backend.dto.shop.request.ProductRecommendationRequest;
import com.backend.dto.shop.response.ProductRecommendationResponse;
import com.backend.service.shop.ProductRecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 상품 추천 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductRecommendationController {
    
    private final ProductRecommendationService productRecommendationService;
    
    /**
     * 조건 기반 상품 추천
     * POST /api/products/recommend
     * 
     * @param request 추천 요청
     * @return 상품 추천 결과 (Top 3)
     */
    @PostMapping("/recommend")
    public ResponseEntity<ProductRecommendationResponse> recommend(
            @Valid @RequestBody ProductRecommendationRequest request) {
        ProductRecommendationResponse response = productRecommendationService.recommend(request);
        return ResponseEntity.ok(response);
    }
}


package com.backend.service.shop;

import com.backend.dto.shop.request.ProductRecommendationRequest;
import com.backend.dto.shop.response.ProductRecommendationResponse;

/**
 * 상품 추천 서비스
 */
public interface ProductRecommendationService {
    
    /**
     * 조건 기반 상품 추천
     * 
     * @param request 추천 요청
     * @return Top 3 상품 추천 결과
     */
    ProductRecommendationResponse recommend(ProductRecommendationRequest request);
}


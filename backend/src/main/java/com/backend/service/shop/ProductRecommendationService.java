package com.backend.service.shop;

import com.backend.dto.shop.request.ProductRecommendationRequest;
import com.backend.dto.shop.response.ProductRecommendationResponse;

public interface ProductRecommendationService {
    ProductRecommendationResponse recommend(ProductRecommendationRequest request);
}


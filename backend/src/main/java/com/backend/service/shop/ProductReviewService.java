package com.backend.service.shop;

import com.backend.common.dto.PageRequest;
import com.backend.common.dto.PageResponse;
import com.backend.dto.shop.request.ReviewCreateRequest;
import com.backend.dto.shop.request.ReviewUpdateRequest;
import com.backend.dto.shop.response.ReviewResponse;

public interface ProductReviewService {

    ReviewResponse create(Long productId, ReviewCreateRequest request, Long memberId);

    PageResponse<ReviewResponse> findByProductId(Long productId, PageRequest pageRequest);

    ReviewResponse update(Long reviewId, ReviewUpdateRequest request, Long memberId);

    void delete(Long reviewId, Long memberId);
}

package com.backend.service.shop;

import com.backend.common.dto.PageRequest;
import com.backend.common.dto.PageResponse;
import com.backend.dto.shop.response.WishlistItemResponse;

/**
 * 찜 서비스 인터페이스
 */
public interface WishlistService {
    
    /**
     * 찜 토글 (추가/제거)
     * 
     * @param memberId 회원 ID
     * @param productId 상품 ID
     * @return 찜 여부 (true: 찜 추가됨, false: 찜 제거됨)
     */
    boolean toggleWishlist(Long memberId, Long productId);
    
    /**
     * 내 찜 목록 조회 (페이징)
     * 
     * @param memberId 회원 ID
     * @param pageRequest 페이징 요청
     * @return 찜 목록 응답
     */
    PageResponse<WishlistItemResponse> findAll(Long memberId, PageRequest pageRequest);
}

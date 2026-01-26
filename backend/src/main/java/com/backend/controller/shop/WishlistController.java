package com.backend.controller.shop;

import com.backend.common.dto.PageRequest;
import com.backend.common.dto.PageResponse;
import com.backend.dto.shop.response.WishlistItemResponse;
import com.backend.dto.shop.response.WishlistToggleResponse;
import com.backend.service.shop.WishlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/wishlists")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    /**
     * 찜 토글 (추가/제거)
     * 
     * @param productId 상품 ID
     * @return 찜 여부
     */
    @PostMapping("/{productId}")
    public ResponseEntity<WishlistToggleResponse> toggleWishlist(
            @PathVariable Long productId) {
        // TODO: 추후 JWT에서 사용자 ID 추출
        // @AuthenticationPrincipal Long userId
        Long memberId = 1L; // 임시 값
        
        boolean wished = wishlistService.toggleWishlist(memberId, productId);
        
        WishlistToggleResponse response = WishlistToggleResponse.builder()
                .wished(wished)
                .build();
        
        return ResponseEntity.ok(response);
    }

    /**
     * 내 찜 목록 조회 (페이징)
     * 
     * @param pageRequest 페이징 요청
     * @return 찜 목록 응답
     */
    @GetMapping
    public ResponseEntity<PageResponse<WishlistItemResponse>> findAll(
            @Valid @ModelAttribute PageRequest pageRequest) {
        // TODO: 추후 JWT에서 사용자 ID 추출
        // @AuthenticationPrincipal Long userId
        Long memberId = 1L; // 임시 값
        
        PageResponse<WishlistItemResponse> response = wishlistService.findAll(memberId, pageRequest);
        return ResponseEntity.ok(response);
    }
}

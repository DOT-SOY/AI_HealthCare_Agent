package com.backend.service.cart;
import com.backend.dto.cart.response.CartResponse;


/**
 * 장바구니 서비스 인터페이스
 */
public interface CartService {
    
    /**
     * 장바구니 조회 또는 생성
     * memberId가 있으면 회원 장바구니, 없으면 guestToken으로 게스트 장바구니
     * 
     * @param memberId 회원 ID (nullable)
     * @param guestToken 게스트 토큰 (nullable)
     * @return 장바구니 ID
     */
    Long getOrCreateCart(Long memberId, String guestToken);
    
    /**
     * 게스트 토큰 생성 유틸리티
     * 
     * @return 새 게스트 토큰
     */
    String generateGuestToken();
    
    /**
     * 장바구니에 아이템 추가
     * 같은 variant가 있으면 수량 증가, 없으면 신규 라인 생성
     * 
     * @param cartKey 장바구니 식별자 (memberId 또는 guestToken)
     * @param variantId 상품 변형 ID
     * @param qty 수량 (>=1)
     */
    void addItem(CartKey cartKey, Long variantId, Integer qty);
    
    /**
     * 장바구니 아이템 수량 변경
     * 
     * @param cartKey 장바구니 식별자
     * @param itemId 장바구니 아이템 ID
     * @param qty 수량 (>=1)
     */
    void updateQty(CartKey cartKey, Long itemId, Integer qty);
    
    /**
     * 장바구니 아이템 제거
     * 
     * @param cartKey 장바구니 식별자
     * @param itemId 장바구니 아이템 ID
     */
    void removeItem(CartKey cartKey, Long itemId);
    
    /**
     * 장바구니 비우기
     * 
     * @param cartKey 장바구니 식별자
     */
    void clearCart(CartKey cartKey);
    
    /**
     * 장바구니 조회 (N+1 방지 최적화 포함)
     * 
     * @param cartKey 장바구니 식별자
     * @return 장바구니 응답 DTO
     */
    CartResponse getCart(CartKey cartKey);
    
    /**
     * 게스트 장바구니를 회원 장바구니로 병합
     * 
     * @param guestToken 게스트 토큰
     * @param memberId 회원 ID
     */
    void mergeGuestCartToMemberCart(String guestToken, Long memberId);
}

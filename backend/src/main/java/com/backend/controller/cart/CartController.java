package com.backend.controller.cart;

import com.backend.dto.cart.request.CartItemAddRequest;
import com.backend.dto.cart.request.CartItemUpdateRequest;
import com.backend.dto.cart.response.CartResponse;
import com.backend.service.cart.CartKey;
import com.backend.service.cart.CartService;
import com.backend.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final CookieUtil cookieUtil;

    /**
     * 장바구니 조회
     * 
     * @param request HTTP 요청 (쿠키 읽기용)
     * @param response HTTP 응답 (쿠키 설정용)
     * @return 장바구니 응답
     */
    @GetMapping
    public ResponseEntity<CartResponse> getCart(
            HttpServletRequest request,
            HttpServletResponse response) {
        CartKey cartKey = resolveCartKey(request, response);
        CartResponse cartResponse = cartService.getCart(cartKey);
        return ResponseEntity.ok(cartResponse);
    }

    /**
     * 장바구니에 아이템 추가
     * 
     * @param request 요청 DTO
     * @param httpRequest HTTP 요청 (쿠키 읽기용)
     * @param httpResponse HTTP 응답 (쿠키 설정용)
     * @return 204 No Content
     */
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> addItem(
            @Valid @RequestBody CartItemAddRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        CartKey cartKey = resolveCartKey(httpRequest, httpResponse);
        cartService.addItem(cartKey, request.getVariantId(), request.getQty());
        return ResponseEntity.noContent().build();
    }

    /**
     * 장바구니 아이템 수량 변경
     * 
     * @param itemId 장바구니 아이템 ID
     * @param request 요청 DTO
     * @param httpRequest HTTP 요청 (쿠키 읽기용)
     * @param httpResponse HTTP 응답 (쿠키 설정용)
     * @return 204 No Content
     */
    @PatchMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> updateQty(
            @PathVariable Long itemId,
            @Valid @RequestBody CartItemUpdateRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        CartKey cartKey = resolveCartKey(httpRequest, httpResponse);
        cartService.updateQty(cartKey, itemId, request.getQty());
        return ResponseEntity.noContent().build();
    }

    /**
     * 장바구니 아이템 제거
     * 
     * @param itemId 장바구니 아이템 ID
     * @param httpRequest HTTP 요청 (쿠키 읽기용)
     * @param httpResponse HTTP 응답 (쿠키 설정용)
     * @return 204 No Content
     */
    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> removeItem(
            @PathVariable Long itemId,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        CartKey cartKey = resolveCartKey(httpRequest, httpResponse);
        cartService.removeItem(cartKey, itemId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 장바구니 비우기
     * 
     * @param httpRequest HTTP 요청 (쿠키 읽기용)
     * @param httpResponse HTTP 응답 (쿠키 설정용)
     * @return 204 No Content
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> clearCart(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        CartKey cartKey = resolveCartKey(httpRequest, httpResponse);
        cartService.clearCart(cartKey);
        return ResponseEntity.noContent().build();
    }

    /**
     * CartKey 해석 (회원 또는 게스트)
     * 회원이면 SecurityContext에서 memberId 획득, 비회원이면 쿠키에서 guestToken 획득
     * 
     * @param request HTTP 요청
     * @param response HTTP 응답 (쿠키 설정용)
     * @return CartKey
     */
    private CartKey resolveCartKey(HttpServletRequest request, HttpServletResponse response) {
        // TODO: 추후 JWT에서 사용자 ID 추출
        // Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // Long memberId = authentication != null ? (Long) authentication.getPrincipal() : null;
        Long memberId = null; // 임시로 null (비회원 처리)
        
        if (memberId != null) {
            // 회원
            return CartKey.ofMember(memberId);
        } else {
            // 비회원: 쿠키에서 guestToken 읽기
            String guestToken = cookieUtil.getGuestToken(request);
            
            if (guestToken == null || guestToken.trim().isEmpty()) {
                // 쿠키가 없으면 새로 생성
                guestToken = cartService.generateGuestToken();
                cookieUtil.setGuestToken(response, guestToken);
            }
            
            return CartKey.ofGuest(guestToken);
        }
    }
}

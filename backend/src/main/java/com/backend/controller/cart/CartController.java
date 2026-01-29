package com.backend.controller.cart;

import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.domain.member.Member;
import com.backend.dto.cart.request.CartItemAddRequest;
import com.backend.dto.cart.request.CartItemUpdateRequest;
import com.backend.dto.cart.response.CartResponse;
import com.backend.service.cart.CartKey;
import com.backend.service.cart.CartService;
import com.backend.service.member.CurrentMemberService;
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
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final CookieUtil cookieUtil;
    private final CurrentMemberService currentMemberService;

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
        log.info("getCart: cartId={}", cartResponse != null ? cartResponse.getCartId() : null);
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
        
        // variantId가 있으면 variantId로 추가, 없으면 productId로 추가
        if (request.getVariantId() != null) {
            cartService.addItem(cartKey, request.getVariantId(), request.getQty());
        } else if (request.getProductId() != null) {
            cartService.addItemByProductId(cartKey, request.getProductId(), request.getQty());
        } else {
            return ResponseEntity.badRequest().build();
        }
        
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
     * 게스트 장바구니를 회원 장바구니로 병합
     * JWT 필수 (인증된 회원만 사용 가능)
     * 
     * @param httpRequest HTTP 요청 (쿠키 읽기용)
     * @param httpResponse HTTP 응답 (쿠키 설정용)
     * @return 병합된 장바구니 응답
     */
    @PostMapping("/merge")
    public ResponseEntity<CartResponse> mergeCart(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        // JWT 인증 필수 확인 + 회원 조회는 CurrentMemberService 를 통해 수행
        final Member member;
        try {
            member = currentMemberService.getCurrentMemberOrThrow();
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.JWT_ERROR || e.getErrorCode() == ErrorCode.MEMBER_NOT_FOUND) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            throw e;
        }
        
        // 게스트 토큰 읽기
        String guestToken = cookieUtil.getGuestToken(httpRequest);
        
        if (guestToken != null && !guestToken.trim().isEmpty()) {
            // 게스트 카트를 회원 카트로 병합
            cartService.mergeGuestCartToMemberCart(guestToken.trim(), member.getId());
            
            // 게스트 토큰 쿠키 삭제
            cookieUtil.removeGuestToken(httpRequest, httpResponse);
        }
        
        // 병합된 회원 카트 반환
        CartKey memberCartKey = CartKey.ofMember(member.getId());
        CartResponse cartResponse = cartService.getCart(memberCartKey);
        return ResponseEntity.ok(cartResponse);
    }

    /**
     * CartKey 해석 (회원 또는 게스트)
     * - 로그인 상태: SecurityContext에서 email → memberId 조회 → 무조건 member_id 기준 (guest_token 무시)
     * - 비로그인: 쿠키 guest_token 사용. 없으면 발급 후 Set-Cookie.
     * - cartId는 getCart 호출 후 응답에서만 알 수 있으므로 getCart() 내부에서 별도 로그.
     */
    private CartKey resolveCartKey(HttpServletRequest request, HttpServletResponse response) {
        String guestToken = cookieUtil.getGuestToken(request);

        String principal = null;
        Long memberId = null;

        try {
            Member member = currentMemberService.getCurrentMemberOrThrow();
            memberId = member.getId();
            principal = member.getEmail();
            CartKey key = CartKey.ofMember(memberId);
            log.info("resolveCartKey: principal={}, memberId={}, guestToken=ignored, cartKey=member:{}",
                    principal, memberId, memberId);
            return key;
        } catch (BusinessException e) {
            // JWT 미인증 또는 회원 없음/삭제 등은 게스트 플로우로 처리
            if (e.getErrorCode() != ErrorCode.JWT_ERROR && e.getErrorCode() != ErrorCode.MEMBER_NOT_FOUND) {
                throw e;
            }
        }

        if (guestToken == null || guestToken.trim().isEmpty()) {
            guestToken = cartService.generateGuestToken();
            cookieUtil.setGuestToken(request, response, guestToken);
        }
        String guestMask = (guestToken != null && guestToken.length() > 8)
                ? guestToken.substring(0, 8) + "..." : (guestToken != null ? guestToken : "null");
        CartKey key = CartKey.ofGuest(guestToken);
        log.info("resolveCartKey: principal={}, memberId=null, guestToken={}, cartKey=guest",
                principal, guestMask);
        return key;
    }
}

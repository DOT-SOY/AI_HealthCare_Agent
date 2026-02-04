package com.backend.controller.cart;

import com.backend.dto.cart.request.CartItemAddRequest;
import com.backend.service.cart.CartKey;
import com.backend.service.cart.CartService;
import com.backend.service.cart.CartIdempotencyService;
import com.backend.service.member.CurrentMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/cart/ai")
@RequiredArgsConstructor
public class CartAIController {
    
    private final CartService cartService;
    private final CartIdempotencyService cartIdempotencyService;
    private final CurrentMemberService currentMemberService;
    
    @PostMapping("/add-item")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> addItemWithIdempotency(
            @Valid @RequestBody CartItemAddRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        
        var member = currentMemberService.getCurrentMemberOrThrow();
        CartKey cartKey = CartKey.ofMember(member.getId());
        
        // 멱등키 검증
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            if (cartIdempotencyService.isDuplicate(idempotencyKey)) {
                log.info("중복 요청 감지 (멱등키): {}", idempotencyKey);
                return ResponseEntity.noContent().build();  // 이미 처리된 요청
            }
        }
        
        // 장바구니 담기
        if (request.getVariantId() != null) {
            cartService.addItem(cartKey, request.getVariantId(), request.getQty());
        } else if (request.getProductId() != null) {
            cartService.addItemByProductId(cartKey, request.getProductId(), request.getQty());
        } else {
            return ResponseEntity.badRequest().build();
        }
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            cartIdempotencyService.saveIdempotencyKey(idempotencyKey);
        }
        
        return ResponseEntity.noContent().build();
    }
}


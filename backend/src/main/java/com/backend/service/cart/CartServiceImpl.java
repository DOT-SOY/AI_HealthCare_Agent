package com.backend.service.cart;

import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.domain.cart.Cart;
import com.backend.domain.cart.CartItem;
import com.backend.domain.member.Member;
import com.backend.domain.shop.ProductImage;
import com.backend.domain.shop.ProductVariant;
import com.backend.dto.cart.response.CartItemResponse;
import com.backend.dto.cart.response.CartResponse;
import com.backend.dto.cart.response.CartTotalsResponse;
import com.backend.repository.cart.CartItemRepository;
import com.backend.repository.cart.CartRepository;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.shop.ProductVariantRepository;
import com.backend.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final MemberRepository memberRepository;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    public Long getOrCreateCart(Long memberId, String guestToken) {
        Cart cart;
        
        if (memberId != null) {
            // 회원 장바구니 조회 또는 생성
            cart = cartRepository.findByMemberId(memberId)
                    .orElseGet(() -> {
                        Member member = memberRepository.findById(memberId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, memberId));
                        Cart newCart = Cart.builder()
                                .member(member)
                                .build();
                        return cartRepository.save(newCart);
                    });
        } else if (guestToken != null && !guestToken.trim().isEmpty()) {
            // 게스트 장바구니 조회 또는 생성
            cart = cartRepository.findByGuestToken(guestToken.trim())
                    .orElseGet(() -> {
                        Cart newCart = Cart.builder()
                                .guestToken(guestToken.trim())
                                .build();
                        return cartRepository.save(newCart);
                    });
        } else {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        
        return cart.getId();
    }

    @Override
    public String generateGuestToken() {
        return UUID.randomUUID().toString();
    }

    @Override
    @Transactional
    public void addItem(CartKey cartKey, Long variantId, Integer qty) {
        if (qty == null || qty < 1) {
            throw new BusinessException(ErrorCode.SHOP_CART_ITEM_INVALID_QUANTITY);
        }

        // Variant 존재 확인
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_VARIANT_NOT_FOUND, variantId));

        // 재고 검증
        if (!variant.isActive()) {
            throw new BusinessException(ErrorCode.SHOP_VARIANT_INACTIVE, variantId);
        }
        
        // 장바구니 조회 및 소유자 확인
        Cart cart = getCartAndVerifyOwnership(cartKey);
        
        // 기존 아이템이 있는지 확인 (재고 검증 전에 확인)
        CartItem existingItem = cartItemRepository.findByCartIdAndVariantId(cart.getId(), variantId)
                .orElse(null);
        
        // 추가할 총 수량 계산
        int totalQty = existingItem != null ? existingItem.getQty() + qty : qty;
        
        // 재고 검증
        if (variant.getStockQty() < totalQty) {
            throw new BusinessException(ErrorCode.SHOP_VARIANT_OUT_OF_STOCK, totalQty, variant.getStockQty());
        }

        if (existingItem != null) {
            // 기존 아이템 수량 증가
            existingItem.increaseQty(qty);
            cartItemRepository.save(existingItem);
            log.info("Cart item quantity increased: cartId={}, itemId={}, variantId={}, qty={}", 
                    cart.getId(), existingItem.getId(), variantId, existingItem.getQty());
        } else {
            // 신규 아이템 생성
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .variant(variant)
                    .qty(qty)
                    .build();
            cart.addItem(newItem);
            cartItemRepository.save(newItem);
            log.info("Cart item added: cartId={}, itemId={}, variantId={}, qty={}", 
                    cart.getId(), newItem.getId(), variantId, qty);
        }
    }

    @Override
    @Transactional
    public void updateQty(CartKey cartKey, Long itemId, Integer qty) {
        if (qty == null) {
            throw new BusinessException(ErrorCode.SHOP_CART_ITEM_INVALID_QUANTITY);
        }

        // qty <= 0이면 삭제 처리
        if (qty <= 0) {
            removeItem(cartKey, itemId);
            return;
        }

        // 장바구니 조회 및 소유자 확인
        Cart cart = getCartAndVerifyOwnership(cartKey);

        // 아이템 조회 및 소유자 확인
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_CART_ITEM_NOT_FOUND, itemId));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new BusinessException(ErrorCode.SHOP_CART_ITEM_ACCESS_DENIED);
        }

        // 재고 검증
        ProductVariant variant = item.getVariant();
        if (!variant.isActive()) {
            throw new BusinessException(ErrorCode.SHOP_VARIANT_INACTIVE, variant.getId());
        }
        if (variant.getStockQty() < qty) {
            throw new BusinessException(ErrorCode.SHOP_VARIANT_OUT_OF_STOCK, qty, variant.getStockQty());
        }

        // 수량 변경
        item.updateQty(qty);
        cartItemRepository.save(item);
        log.info("Cart item quantity updated: cartId={}, itemId={}, qty={}", cart.getId(), itemId, qty);
    }

    @Override
    @Transactional
    public void removeItem(CartKey cartKey, Long itemId) {
        // 장바구니 조회 및 소유자 확인
        Cart cart = getCartAndVerifyOwnership(cartKey);

        // 아이템 조회 및 소유자 확인
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_CART_ITEM_NOT_FOUND, itemId));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new BusinessException(ErrorCode.SHOP_CART_ITEM_ACCESS_DENIED);
        }

        // 아이템 제거
        cart.removeItem(item);
        cartItemRepository.delete(item);
        log.info("Cart item removed: cartId={}, itemId={}", cart.getId(), itemId);
    }

    @Override
    @Transactional
    public void clearCart(CartKey cartKey) {
        // 장바구니 조회 및 소유자 확인
        Cart cart = getCartAndVerifyOwnership(cartKey);

        // 모든 아이템 제거
        cartItemRepository.deleteByCartId(cart.getId());
        log.info("Cart cleared: cartId={}", cart.getId());
    }

    @Override
    @Transactional
    public CartResponse getCart(CartKey cartKey) {
        Cart cart;
        
        // N+1 방지를 위해 EntityGraph로 items, variant, product, images를 한 번에 조회
        if (cartKey.isMember()) {
            cart = cartRepository.findWithItemsByMemberId(cartKey.getMemberId())
                    .orElseGet(() -> {
                        // 회원 카트가 없으면 생성
                        Member member = memberRepository.findById(cartKey.getMemberId())
                                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, cartKey.getMemberId()));
                        Cart newCart = Cart.builder()
                                .member(member)
                                .build();
                        return cartRepository.save(newCart);
                    });
            
            // 회원 장바구니인지 확인
            if (cart.getMember() == null || !cart.getMember().getId().equals(cartKey.getMemberId())) {
                throw new BusinessException(ErrorCode.SHOP_CART_ACCESS_DENIED);
            }
        } else {
            cart = cartRepository.findWithItemsByGuestToken(cartKey.getGuestToken())
                    .orElseGet(() -> {
                        // 게스트 카트가 없으면 생성
                        Cart newCart = Cart.builder()
                                .guestToken(cartKey.getGuestToken())
                                .build();
                        return cartRepository.save(newCart);
                    });
            
            // 게스트 장바구니인지 확인
            if (cart.getGuestToken() == null || !cart.getGuestToken().equals(cartKey.getGuestToken())) {
                throw new BusinessException(ErrorCode.SHOP_CART_ACCESS_DENIED);
            }
        }
        
        // CartItemResponse 변환
        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(this::toCartItemResponse)
                .collect(Collectors.toList());
        
        // Totals 계산
        CartTotalsResponse totals = calculateTotals(cart.getItems());
        
        return CartResponse.builder()
                .cartId(cart.getId())
                .isGuest(cart.getGuestToken() != null)
                .items(itemResponses)
                .totals(totals)
                .build();
    }
    
    @Override
    @Transactional
    public void mergeGuestCartToMemberCart(String guestToken, Long memberId) {
        // 1. 게스트 장바구니 조회
        Cart guestCart = cartRepository.findByGuestToken(guestToken)
                .orElse(null);
        
        // 게스트 카트가 없거나 비어있으면 종료
        if (guestCart == null || guestCart.getItems().isEmpty()) {
            log.info("Guest cart is empty or not found, skipping merge: guestToken={}", guestToken);
            return;
        }
        
        // 2. 회원 장바구니 조회 또는 생성
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, memberId));
        
        Cart memberCart = cartRepository.findByMemberId(memberId)
                .orElseGet(() -> {
                    Cart newCart = Cart.builder()
                            .member(member)
                            .build();
                    return cartRepository.save(newCart);
                });
        
        // 3. 게스트 장바구니의 아이템들을 회원 장바구니로 병합 (중복 variant는 수량 합산)
        for (CartItem guestItem : guestCart.getItems()) {
            ProductVariant variant = guestItem.getVariant();
            
            // 재고 검증
            if (!variant.isActive()) {
                log.warn("Skipping inactive variant during merge: variantId={}", variant.getId());
                continue;
            }
            
            CartItem existingItem = cartItemRepository
                    .findByCartIdAndVariantId(memberCart.getId(), variant.getId())
                    .orElse(null);
            
            if (existingItem != null) {
                // 기존 아이템이 있으면 수량 합산
                int newQty = existingItem.getQty() + guestItem.getQty();
                
                // 재고 검증
                if (variant.getStockQty() < newQty) {
                    log.warn("Insufficient stock during merge, using max available: variantId={}, requested={}, available={}", 
                            variant.getId(), newQty, variant.getStockQty());
                    newQty = variant.getStockQty();
                }
                
                existingItem.updateQty(newQty);
                cartItemRepository.save(existingItem);
                log.info("Merged cart item: cartId={}, variantId={}, qty={}", 
                        memberCart.getId(), variant.getId(), newQty);
            } else {
                // 신규 아이템 추가
                // 재고 검증
                int qty = guestItem.getQty();
                if (variant.getStockQty() < qty) {
                    log.warn("Insufficient stock during merge, using max available: variantId={}, requested={}, available={}", 
                            variant.getId(), qty, variant.getStockQty());
                    qty = variant.getStockQty();
                }
                
                CartItem newItem = CartItem.builder()
                        .cart(memberCart)
                        .variant(variant)
                        .qty(qty)
                        .build();
                memberCart.addItem(newItem);
                cartItemRepository.save(newItem);
                log.info("Added cart item from guest cart: cartId={}, variantId={}, qty={}", 
                        memberCart.getId(), variant.getId(), qty);
            }
        }
        
        // 4. 게스트 장바구니 삭제
        cartItemRepository.deleteByCartId(guestCart.getId());
        cartRepository.delete(guestCart);
        log.info("Guest cart merged and deleted: guestToken={}, memberId={}", guestToken, memberId);
    }
    
    /**
     * CartItem을 CartItemResponse로 변환
     */
    private CartItemResponse toCartItemResponse(CartItem item) {
        ProductVariant variant = item.getVariant();
        var product = variant.getProduct();
        
        // Primary 이미지 찾기
        String primaryImageUrl = null;
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            primaryImageUrl = product.getImages().stream()
                    .filter(ProductImage::isPrimaryImage)
                    .min(Comparator.comparing(ProductImage::getCreatedAt))
                    .map(image -> {
                        String filePath = image.getFilePath();
                        return (filePath != null && !filePath.trim().isEmpty())
                                ? fileStorageService.getFileUrl(filePath)
                                : null;
                    })
                    .orElse(null);
        }
        
        // 가격 결정: variant.price가 있으면 사용, 없으면 product.basePrice
        BigDecimal price = variant.getPrice() != null 
                ? variant.getPrice() 
                : product.getBasePrice();
        
        return CartItemResponse.builder()
                .itemId(item.getId())
                .variantId(variant.getId())
                .qty(item.getQty())
                .productId(product.getId())
                .productName(product.getName())
                .price(price)
                .optionSummary(variant.getOptionText())
                .primaryImageUrl(primaryImageUrl)
                .build();
    }
    
    /**
     * 장바구니 총계 계산
     */
    private CartTotalsResponse calculateTotals(List<CartItem> items) {
        int itemCount = items.size();
        int totalQty = items.stream()
                .mapToInt(CartItem::getQty)
                .sum();
        
        // TODO: 전체 가격 합계 계산 (variant.price 또는 product.basePrice 사용)
        BigDecimal totalPrice = BigDecimal.ZERO;
        for (CartItem item : items) {
            ProductVariant variant = item.getVariant();
            BigDecimal itemPrice = variant.getPrice() != null 
                    ? variant.getPrice() 
                    : variant.getProduct().getBasePrice();
            totalPrice = totalPrice.add(itemPrice.multiply(BigDecimal.valueOf(item.getQty())));
        }
        
        return CartTotalsResponse.builder()
                .itemCount(itemCount)
                .totalQty(totalQty)
                .totalPrice(totalPrice)
                .build();
    }

    /**
     * 장바구니 조회 및 소유자 확인
     * 
     * @param cartKey 장바구니 식별자
     * @return 장바구니 엔티티
     */
    private Cart getCartAndVerifyOwnership(CartKey cartKey) {
        Cart cart;
        
        if (cartKey.isMember()) {
            cart = cartRepository.findByMemberId(cartKey.getMemberId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_CART_NOT_FOUND));
            
            // 회원 장바구니인지 확인
            if (cart.getMember() == null || !cart.getMember().getId().equals(cartKey.getMemberId())) {
                throw new BusinessException(ErrorCode.SHOP_CART_ACCESS_DENIED);
            }
        } else {
            cart = cartRepository.findByGuestToken(cartKey.getGuestToken())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_CART_NOT_FOUND));
            
            // 게스트 장바구니인지 확인
            if (cart.getGuestToken() == null || !cart.getGuestToken().equals(cartKey.getGuestToken())) {
                throw new BusinessException(ErrorCode.SHOP_CART_ACCESS_DENIED);
            }
        }
        
        return cart;
    }
}

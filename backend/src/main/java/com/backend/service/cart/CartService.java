package com.backend.service.cart;
import com.backend.dto.cart.response.CartResponse;

public interface CartService {

    Long getOrCreateCart(Long memberId, String guestToken);

    String generateGuestToken();

    void addItem(CartKey cartKey, Long variantId, Integer qty);

    void addItemByProductId(CartKey cartKey, Long productId, Integer qty);

    void updateQty(CartKey cartKey, Long itemId, Integer qty);

    void removeItem(CartKey cartKey, Long itemId);

    void clearCart(CartKey cartKey);

    CartResponse getCart(CartKey cartKey);

    void mergeGuestCartToMemberCart(String guestToken, Long memberId);
}

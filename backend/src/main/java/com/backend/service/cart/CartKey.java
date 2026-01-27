package com.backend.service.cart;

import lombok.Getter;

/**
 * 장바구니 식별자 (회원 또는 게스트)
 * memberId와 guestToken 중 하나만 존재해야 함
 */
@Getter
public class CartKey {
    private final Long memberId;
    private final String guestToken;

    private CartKey(Long memberId, String guestToken) {
        if (memberId != null && guestToken != null) {
            throw new IllegalArgumentException("memberId와 guestToken은 동시에 설정할 수 없습니다.");
        }
        if (memberId == null && guestToken == null) {
            throw new IllegalArgumentException("memberId 또는 guestToken 중 하나는 필수입니다.");
        }
        this.memberId = memberId;
        this.guestToken = guestToken;
    }

    public static CartKey ofMember(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId는 null일 수 없습니다.");
        }
        return new CartKey(memberId, null);
    }

    public static CartKey ofGuest(String guestToken) {
        if (guestToken == null || guestToken.trim().isEmpty()) {
            throw new IllegalArgumentException("guestToken은 null이거나 빈 문자열일 수 없습니다.");
        }
        return new CartKey(null, guestToken.trim());
    }

    public boolean isMember() {
        return memberId != null;
    }

    public boolean isGuest() {
        return guestToken != null;
    }
}

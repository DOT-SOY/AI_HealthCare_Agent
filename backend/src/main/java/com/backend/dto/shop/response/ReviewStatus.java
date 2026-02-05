package com.backend.dto.shop.response;

/**
 * 상품에 대한 현재 로그인 회원의 리뷰 상태
 */
public enum ReviewStatus {

    // 로그인되지 않은 상태
    NOT_LOGGED_IN,

    // 로그인은 했지만 해당 상품을 구매한 적이 없음
    NOT_PURCHASED,

    // 상품을 구매했고 아직 이 상품에 내 리뷰가 없음 (새 리뷰 작성 가능)
    CAN_REVIEW,

    // 상품을 구매했고 이미 이 상품에 내 리뷰가 한 번 있음
    ALREADY_REVIEWED
}



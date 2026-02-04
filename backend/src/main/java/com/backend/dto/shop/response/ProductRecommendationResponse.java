package com.backend.dto.shop.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRecommendationResponse {

    /** 요청한 keyword/must_have 조건에 맞는 상품 목록. 0건이면 비어 있음. */
    private List<ProductRecommendationItem> products;

    /**
     * true: 조건에 맞는 상품이 있어 products에 반영됨.
     * false: keyword 또는 must_have 등 조건으로 검색했으나 0건이어서 products가 비어 있음.
     * null: 조건 검색과 무관(예: keyword 없이 조회).
     */
    private Boolean conditionMatched;

    /**
     * 조건에 맞는 상품이 0건일 때만 참고용으로 제안할 수 있는 대안(동일 카테고리 등).
     * 메인 추천(products)과 책임을 분리해, "조건에 맞는 상품 없음"을 명시한 뒤 별도로만 제안할 때 사용.
     */
    private List<ProductRecommendationItem> alternativeCandidates;
}


package com.backend.repository.shop;

import com.backend.domain.shop.Product;
import com.backend.domain.shop.ProductStatus;
import com.backend.domain.shop.QProduct;
import com.backend.domain.shop.QProductCategory;
import com.backend.domain.shop.QProductVariant;
import com.backend.domain.shop.QCategory;
import com.querydsl.core.types.Order;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ProductSearchImpl implements ProductSearch {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Product> search(ProductSearchCondition condition, Pageable pageable) {
        QProduct product = QProduct.product;
        QProductCategory productCategory = QProductCategory.productCategory;
        QCategory category = QCategory.category;

        // 기본 쿼리 (2-쿼리 전략: images는 별도 조회)
        // ManyToOne인 createdBy만 페치 조인 (OneToMany인 images는 제외)
        JPAQuery<Product> query = queryFactory
                .selectFrom(product)
                .leftJoin(product.createdBy).fetchJoin();  // Member 페치 조인 (ManyToOne만)
        
        // 카테고리 필터가 있을 때만 조인
        if (condition.getCategoryId() != null) {
            query.leftJoin(productCategory).on(product.id.eq(productCategory.product.id))
                 .leftJoin(category).on(productCategory.category.id.eq(category.id));
        }
        
        query.where(
                        notDeleted(product),
                        keywordContains(condition.getKeyword()),
                        categoryIdEq(condition.getCategoryId(), productCategory, category),
                        priceBetween(condition.getMinPrice(), condition.getMaxPrice()),
                        statusEq(condition.getStatus()),
                        excludeOutOfStock(condition, product)
                );
        
        // 카테고리 조인 시 중복 방지를 위해 distinct 사용
        if (condition.getCategoryId() != null) {
            query.distinct();
        }

        // 정렬 적용
        query.orderBy(getOrderSpecifier(condition, product));

        // 페이징 적용
        List<Product> content = query
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 카운트 쿼리 (성능 최적화 - 카운트만 수행)
        JPAQuery<Long> countQuery = queryFactory
                .select(product.countDistinct())
                .from(product);
        
        // 카테고리 필터가 있을 때만 조인 (메인 쿼리와 동일하게)
        if (condition.getCategoryId() != null) {
            countQuery.leftJoin(productCategory).on(product.id.eq(productCategory.product.id))
                     .leftJoin(category).on(productCategory.category.id.eq(category.id));
        }
        
        countQuery.where(
                        notDeleted(product),
                        keywordContains(condition.getKeyword()),
                        categoryIdEq(condition.getCategoryId(), productCategory, category),
                        priceBetween(condition.getMinPrice(), condition.getMaxPrice()),
                        statusEq(condition.getStatus()),
                        excludeOutOfStock(condition, product)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    // 삭제되지 않은 상품만 조회
    private BooleanExpression notDeleted(QProduct product) {
        return product.deletedAt.isNull();
    }

    // 키워드 검색 (상품명만 - description은 CLOB 타입이라 lower() 함수 사용 불가)
    private BooleanExpression keywordContains(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        QProduct product = QProduct.product;
        // description은 @Lob(CLOB) 타입이므로 containsIgnoreCase 사용 불가
        // 상품명만 검색하도록 수정
        return product.name.containsIgnoreCase(keyword);
    }

    // 카테고리 필터
    private BooleanExpression categoryIdEq(Long categoryId, 
                                           QProductCategory productCategory, 
                                           QCategory category) {
        if (categoryId == null) {
            return null;
        }
        // ProductCategory를 통해 카테고리 필터링
        // categoryId가 null이 아닐 때만 호출되므로 안전
        return productCategory.category.id.eq(categoryId);
    }

    // 가격 범위 필터
    private BooleanExpression priceBetween(BigDecimal minPrice, BigDecimal maxPrice) {
        QProduct product = QProduct.product;
        BooleanExpression expression = null;

        if (minPrice != null) {
            expression = product.basePrice.goe(minPrice);
        }
        if (maxPrice != null) {
            BooleanExpression maxExpression = product.basePrice.loe(maxPrice);
            expression = expression != null 
                    ? expression.and(maxExpression) 
                    : maxExpression;
        }

        return expression;
    }

    // 상태 필터
    private BooleanExpression statusEq(ProductStatus status) {
        if (status == null) {
            return null;
        }
        return QProduct.product.status.eq(status);
    }

    // 품절 제외: variant가 없거나, 재고가 1개 이상인 variant가 있는 상품만 포함
    private BooleanExpression excludeOutOfStock(ProductSearchCondition condition, QProduct product) {
        if (!condition.isExcludeOutOfStock()) {
            return null;
        }
        QProductVariant variant = QProductVariant.productVariant;
        // 품절이 아닌 상품 = variant가 없음 OR (재고 > 0인 variant가 1개 이상 있음)
        BooleanExpression hasNoVariants = JPAExpressions.selectOne()
                .from(variant)
                .where(variant.product.id.eq(product.id))
                .exists()
                .not();
        BooleanExpression hasInStockVariant = JPAExpressions.selectOne()
                .from(variant)
                .where(variant.product.id.eq(product.id), variant.stockQty.gt(0))
                .exists();
        return hasNoVariants.or(hasInStockVariant);
    }

    // 정렬 조건
    private OrderSpecifier<?>[] getOrderSpecifier(ProductSearchCondition condition, QProduct product) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();

        String sortBy = condition.getSortBy() != null ? condition.getSortBy() : "createdAt";
        String direction = condition.getDirection() != null ? condition.getDirection() : "DESC";
        Order order = "ASC".equalsIgnoreCase(direction) ? Order.ASC : Order.DESC;

        switch (sortBy.toLowerCase()) {
            case "baseprice":
            case "price":
                orders.add(new OrderSpecifier<>(order, product.basePrice));
                break;
            case "popularity":
            case "sales":
                // TODO: 추후 판매량 통계 테이블 추가 시 구현
                // 현재는 createdAt으로 대체
                orders.add(new OrderSpecifier<>(order, product.createdAt));
                break;
            case "createdat":
            case "created_at":
            default:
                orders.add(new OrderSpecifier<>(order, product.createdAt));
                break;
        }

        // 기본 정렬 추가 (동일한 값일 때 일관성 보장)
        orders.add(new OrderSpecifier<>(Order.DESC, product.id));

        return orders.toArray(new OrderSpecifier[0]);
    }
}

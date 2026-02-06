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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ProductSearchImpl implements ProductSearch {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Product> search(ProductSearchCondition condition, Pageable pageable) {
        QProduct product = QProduct.product;

        if (condition.getProductIds() != null && !condition.getProductIds().isEmpty()) {
            return searchByProductIds(condition, pageable, product);
        }

        QProductCategory productCategory = QProductCategory.productCategory;
        QCategory category = QCategory.category;

        JPAQuery<Product> query = queryFactory
                .selectFrom(product)
                .leftJoin(product.createdBy).fetchJoin();
        if (condition.getCategoryId() != null) {
            query.leftJoin(productCategory).on(product.id.eq(productCategory.product.id))
                 .leftJoin(category).on(productCategory.category.id.eq(category.id));
        }
        
        query.where(
                        notDeleted(product),
                        keywordContains(condition.getKeyword(), condition.getSearchType()),
                        categoryIdEq(condition.getCategoryId(), productCategory, category),
                        priceBetween(condition.getMinPrice(), condition.getMaxPrice()),
                        statusEq(condition.getStatus()),
                        excludeOutOfStock(condition, product),
                        nameNotContainingAny(product, condition.getExcludeNameKeywords())
                );
        if (condition.getCategoryId() != null) {
            query.distinct();
        }
        query.orderBy(getOrderSpecifier(condition, product));
        List<Product> content = query
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(product.countDistinct())
                .from(product);
        if (condition.getCategoryId() != null) {
            countQuery.leftJoin(productCategory).on(product.id.eq(productCategory.product.id))
                     .leftJoin(category).on(productCategory.category.id.eq(category.id));
        }
        countQuery.where(
                        notDeleted(product),
                        keywordContains(condition.getKeyword(), condition.getSearchType()),
                        categoryIdEq(condition.getCategoryId(), productCategory, category),
                        priceBetween(condition.getMinPrice(), condition.getMaxPrice()),
                        statusEq(condition.getStatus()),
                        excludeOutOfStock(condition, product),
                        nameNotContainingAny(product, condition.getExcludeNameKeywords())
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private Page<Product> searchByProductIds(ProductSearchCondition condition, Pageable pageable, QProduct product) {
        List<Long> productIds = condition.getProductIds();
        int fetchLimit = Math.min(productIds.size(), 300);

        JPAQuery<Product> query = queryFactory
                .selectFrom(product)
                .leftJoin(product.createdBy).fetchJoin()
                .where(
                        notDeleted(product),
                        product.id.in(productIds),
                        keywordContains(condition.getKeyword(), condition.getSearchType()),
                        priceBetween(condition.getMinPrice(), condition.getMaxPrice()),
                        statusEq(condition.getStatus()),
                        excludeOutOfStock(condition, product),
                        nameNotContainingAny(product, condition.getExcludeNameKeywords())
                );

        List<Product> content = query.limit(fetchLimit).fetch();
        Map<Long, Integer> idToIndex = new HashMap<>();
        for (int i = 0; i < productIds.size(); i++) {
            idToIndex.put(productIds.get(i), i);
        }
        List<Product> sorted = content.stream()
                .sorted(Comparator.comparingInt(p -> idToIndex.getOrDefault(p.getId(), Integer.MAX_VALUE)))
                .collect(Collectors.toList());

        int total = sorted.size();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), total);
        List<Product> pageContent = start < total ? sorted.subList(start, end) : List.of();

        return PageableExecutionUtils.getPage(pageContent, pageable, () -> (long) total);
    }

    // 삭제되지 않은 상품만 조회
    private BooleanExpression notDeleted(QProduct product) {
        return product.deletedAt.isNull();
    }

    /**
     * 키워드 검색
     * - 단어 1개: 상품명/설명 중 하나에만 포함되어도 매칭.
     * - 단어 2개 이상: 각 단어 중 하나라도 상품명/설명에 포함되면 매칭(OR 조건).
     *   예: "다이어트 보충제" → 이름이나 설명에 "다이어트" 또는 "보충제"가 포함되면 후보에 포함.
     *   이후 정렬/스코어링은 상위 서비스(ProductRecommendationServiceImpl)에서 수행.
     */
    private BooleanExpression keywordContains(String keyword, String searchType) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        String k = keyword.trim();
        QProduct product = QProduct.product;
        String type = (searchType != null && !searchType.isBlank()) ? searchType.trim().toLowerCase() : "all";

        List<String> tokens = Arrays.stream(k.split("\\s+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (tokens.isEmpty()) {
            return null;
        }

        if (tokens.size() == 1) {
            String single = tokens.get(0);
            BooleanExpression descMatch = product.description.like(likePattern(single), '\\');
            return switch (type) {
                case "name" -> product.name.containsIgnoreCase(single);
                case "description" -> descMatch;
                default -> product.name.containsIgnoreCase(single).or(descMatch);
            };
        }

        // 여러 단어인 경우: 모든 단어 AND가 아니라, 단어들 중 하나라도 매칭되면 포함(OR 조건).
        // 너무 빡센 AND 조건 때문에 검색 0건이 되는 상황을 줄이기 위함.
        BooleanExpression orExpr = null;
        for (String token : tokens) {
            BooleanExpression tokenMatch = switch (type) {
                case "name" -> product.name.containsIgnoreCase(token);
                case "description" -> product.description.like(likePattern(token), '\\');
                default -> product.name.containsIgnoreCase(token).or(product.description.like(likePattern(token), '\\'));
            };
            orExpr = orExpr == null ? tokenMatch : orExpr.or(tokenMatch);
        }
        return orExpr;
    }

    private static String likePattern(String keyword) {
        return "%" + keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private BooleanExpression nameNotContainingAny(QProduct product, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return null;
        }
        BooleanExpression expression = null;
        for (String kw : keywords) {
            if (kw == null || kw.isBlank()) {
                continue;
            }
            String pattern = likePattern(kw.trim().toLowerCase());
            BooleanExpression notLike = product.name.lower().notLike(pattern);
            expression = expression == null ? notLike : expression.and(notLike);
        }
        return expression;
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

    private BooleanExpression statusEq(ProductStatus status) {
        if (status == null) {
            return null;
        }
        return QProduct.product.status.eq(status);
    }

    private BooleanExpression excludeOutOfStock(ProductSearchCondition condition, QProduct product) {
        if (!condition.isExcludeOutOfStock()) {
            return null;
        }
        QProductVariant variant = QProductVariant.productVariant;
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
                orders.add(new OrderSpecifier<>(order, product.createdAt));
                break;
            case "createdat":
            case "created_at":
            default:
                orders.add(new OrderSpecifier<>(order, product.createdAt));
                break;
        }
        orders.add(new OrderSpecifier<>(Order.DESC, product.id));

        return orders.toArray(new OrderSpecifier[0]);
    }
}

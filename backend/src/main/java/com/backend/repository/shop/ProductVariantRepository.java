package com.backend.repository.shop;

import com.backend.domain.shop.ProductVariant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    List<ProductVariant> findByProductId(Long productId);
    Optional<ProductVariant> findBySku(String sku);
    List<ProductVariant> findByActiveTrue();
    
    /**
     * 여러 상품 ID에 해당하는 모든 variant를 한 번에 조회합니다.
     * 2-쿼리 전략에서 사용 (N+1 문제 방지).
     * product 페치 조인으로 grouping 시 N+1 방지.
     */
    @EntityGraph(attributePaths = {"product"})
    @Query("select pv from ProductVariant pv where pv.product.id in :productIds")
    List<ProductVariant> findByProductIdIn(@Param("productIds") List<Long> productIds);
}

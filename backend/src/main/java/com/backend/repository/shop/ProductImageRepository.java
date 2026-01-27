package com.backend.repository.shop;

import com.backend.domain.shop.ProductImage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {
    List<ProductImage> findByProductIdAndDeletedAtIsNull(Long productId);
    List<ProductImage> findByProductIdAndPrimaryImageTrueAndDeletedAtIsNull(Long productId);

    /**
     * 상품의 모든 primary 이미지를 조회합니다.
     */
    List<ProductImage> findByProductIdAndPrimaryImageAndDeletedAtIsNull(Long productId, boolean primaryImage);

    /**
     * 상품 ID와 UUID로 이미지를 조회합니다.
     */
    java.util.Optional<ProductImage> findByProductIdAndUuidAndDeletedAtIsNull(Long productId, UUID uuid);

    /**
     * 여러 상품 ID에 해당하는 모든 이미지를 한 번에 조회합니다.
     * 2-쿼리 전략에서 사용 (N+1 문제 방지).
     * product 페치 조인으로 grouping 시 N+1 방지.
     */
    @EntityGraph(attributePaths = {"product"})
    @Query("select pi from ProductImage pi where pi.product.id in :productIds and pi.deletedAt is null")
    List<ProductImage> findByProductIdIn(@Param("productIds") List<Long> productIds);
    
    // 기존 메서드 호환성을 위한 별칭 (deprecated)
    @Deprecated
    default List<ProductImage> findByProductId(Long productId) {
        return findByProductIdAndDeletedAtIsNull(productId);
    }
    
    @Deprecated
    default List<ProductImage> findByProductIdAndPrimaryImageTrue(Long productId) {
        return findByProductIdAndPrimaryImageTrueAndDeletedAtIsNull(productId);
    }
    
    @Deprecated
    default List<ProductImage> findByProductIdAndPrimaryImage(Long productId, boolean primaryImage) {
        return findByProductIdAndPrimaryImageAndDeletedAtIsNull(productId, primaryImage);
    }
    
    @Deprecated
    default java.util.Optional<ProductImage> findByProductIdAndUuid(Long productId, UUID uuid) {
        return findByProductIdAndUuidAndDeletedAtIsNull(productId, uuid);
    }
}

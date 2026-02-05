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

    List<ProductImage> findByProductIdAndPrimaryImageAndDeletedAtIsNull(Long productId, boolean primaryImage);
    java.util.Optional<ProductImage> findByProductIdAndUuidAndDeletedAtIsNull(Long productId, UUID uuid);

    @EntityGraph(attributePaths = {"product"})
    @Query("select pi from ProductImage pi where pi.product.id in :productIds and pi.deletedAt is null")
    List<ProductImage> findByProductIdIn(@Param("productIds") List<Long> productIds);

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

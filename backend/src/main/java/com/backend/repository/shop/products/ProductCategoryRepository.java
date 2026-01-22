package com.backend.repository.shop.products;

import com.backend.domain.shop.ProductCategory;
import com.backend.domain.shop.ProductCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, ProductCategoryId> {
    List<ProductCategory> findByProductId(Long productId);
    List<ProductCategory> findByCategoryId(Long categoryId);
}

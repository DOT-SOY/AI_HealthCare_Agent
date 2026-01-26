package com.backend.repository.shop;

import com.backend.domain.shop.ProductCategory;
import com.backend.domain.shop.ProductCategoryId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, ProductCategoryId> {
    // 페치 조인으로 N+1 문제 방지 (category, category.parent 한 번에 조회)
    @EntityGraph(attributePaths = {"category", "category.parent"})
    List<ProductCategory> findById_ProductId(Long productId);

    List<ProductCategory> findByCategoryId(Long categoryId);
}

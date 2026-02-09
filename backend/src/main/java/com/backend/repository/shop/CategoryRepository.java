package com.backend.repository.shop;

import com.backend.domain.shop.Category;
import com.backend.domain.shop.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByParentIsNull();
    List<Category> findByParentId(Long parentId);
    List<Category> findByCategoryType(CategoryType categoryType);
    /** 동일 타입 루트가 여러 개일 수 있으므로 정렬 후 첫 1건만 반환 (NonUniqueResultException 방지) */
    Optional<Category> findFirstByCategoryTypeAndParentIsNullOrderBySortOrderAsc(CategoryType categoryType);
}

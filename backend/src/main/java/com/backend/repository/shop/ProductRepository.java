package com.backend.repository.shop;

import com.backend.domain.shop.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsByName(String name);
    
    // 페치 조인으로 N+1 문제 방지 (createdBy, images를 한 번에 조회)
    @EntityGraph(attributePaths = {"createdBy", "images"})
    Optional<Product> findByIdAndDeletedAtIsNull(Long id);
    
    Optional<Product> findByNameAndDeletedAtIsNull(String name);
}

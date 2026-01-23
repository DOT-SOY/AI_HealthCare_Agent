package com.backend.repository.shop;

import com.backend.domain.shop.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsByName(String name);
    
    // @BatchSize로 N+1 문제 방지 (Product 엔티티에 @BatchSize 적용됨)
    Optional<Product> findByIdAndDeletedAtIsNull(Long id);
    
    Optional<Product> findByNameAndDeletedAtIsNull(String name);
}

package com.backend.repository.shop.products;

import com.backend.domain.shop.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsByName(String name);
    
    Optional<Product> findByIdAndDeletedAtIsNull(Long id);
    
    Optional<Product> findByNameAndDeletedAtIsNull(String name);
}

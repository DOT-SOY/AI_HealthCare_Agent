package com.backend.repository.shop;

import com.backend.domain.shop.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsByName(String name);
    
    // 페치 조인으로 N+1 문제 방지 (createdBy만 fetch, images와 variants는 별도 조회)
    // MultipleBagFetchException 방지: 여러 컬렉션을 동시에 fetch join할 수 없음
    @EntityGraph(attributePaths = {"createdBy"})
    Optional<Product> findByIdAndDeletedAtIsNull(Long id);
    
    Optional<Product> findByNameAndDeletedAtIsNull(String name);
}

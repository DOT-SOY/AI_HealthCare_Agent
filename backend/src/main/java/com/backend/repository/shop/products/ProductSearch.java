package com.backend.repository.shop.products;

import com.backend.domain.shop.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductSearch {
    Page<Product> search(ProductSearchCondition condition, Pageable pageable);
}

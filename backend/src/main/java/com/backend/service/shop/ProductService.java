package com.backend.service.shop;

import com.backend.common.dto.PageRequest;
import com.backend.common.dto.PageResponse;
import com.backend.dto.shop.request.ProductCreateRequest;
import com.backend.dto.shop.request.ProductSearchRequest;
import com.backend.dto.shop.request.ProductUpdateRequest;
import com.backend.dto.shop.response.ProductResponse;

public interface ProductService {
    ProductResponse create(ProductCreateRequest request, Long createdBy);
    
    ProductResponse findById(Long id);
    
    PageResponse<ProductResponse> findAll(PageRequest pageRequest, ProductSearchRequest searchRequest);
    
    ProductResponse update(Long id, ProductUpdateRequest request);
    
    void delete(Long id);
}

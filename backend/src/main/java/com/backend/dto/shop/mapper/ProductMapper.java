package com.backend.dto.shop.mapper;

import com.backend.domain.shop.Product;
import com.backend.dto.shop.response.ProductResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProductMapper {
    ProductResponse toResponse(Product product);
}

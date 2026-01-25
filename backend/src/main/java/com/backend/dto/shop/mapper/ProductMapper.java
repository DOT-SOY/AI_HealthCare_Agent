package com.backend.dto.shop.mapper;

import com.backend.domain.shop.Product;
import com.backend.dto.shop.response.ProductResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProductMapper {
    @Mapping(source = "createdBy.id", target = "createdBy")
    ProductResponse toResponse(Product product);
}

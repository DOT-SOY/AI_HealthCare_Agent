package com.backend.dto.shop.mapper;

import com.backend.domain.shop.Product;
import com.backend.dto.shop.response.ProductResponse;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProductMapper {
    @Mapping(target = "createdBy", ignore = true) // @AfterMapping에서 수동 처리
    @Mapping(target = "images", ignore = true) // images는 서비스 레이어에서 처리
    ProductResponse toResponse(Product product);

    @AfterMapping
    default void mapCreatedBy(@MappingTarget ProductResponse response, Product product) {
        if (product.getCreatedBy() != null) {
            response.setCreatedBy(product.getCreatedBy().getId());
        }
    }
}

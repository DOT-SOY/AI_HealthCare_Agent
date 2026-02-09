package com.backend.dto.shop.response;

import com.backend.domain.shop.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {
    private Long id;
    private String name;
    private String categoryType;
    private Long parentId;

    public static CategoryResponse from(Category category) {
        if (category == null) {
            return null;
        }
        
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .categoryType(category.getCategoryType().name())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .build();
    }
}

package com.backend.dto.shop.response;

import com.backend.domain.shop.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 카테고리 응답 DTO
 * API 설계 원칙에 따라 명확한 필드명과 일관된 응답 구조 사용
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {
    private Long id;
    private String name; // categoryType의 displayName
    private String categoryType; // enum 값
    private Long parentId; // 부모 카테고리 ID (null이면 루트)

    /**
     * Category 엔티티로부터 CategoryResponse를 생성하는 정적 팩토리 메서드
     * 
     * @param category Category 엔티티
     * @return CategoryResponse
     */
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

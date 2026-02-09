package com.backend.domain.shop;

import com.backend.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "categories")
public class Category extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @OneToMany(mappedBy = "parent")
    private final List<Category> children = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "category_type", nullable = false, length = 50)
    private CategoryType categoryType;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Builder
    public Category(Category parent,
                   CategoryType categoryType,
                   Integer sortOrder) {
        if (categoryType == null) {
            throw new IllegalArgumentException("카테고리 타입은 필수입니다.");
        }
        this.parent = parent;
        this.categoryType = categoryType;
        this.sortOrder = (sortOrder != null) ? sortOrder : 0;
    }

    // 부모 카테고리 변경
    public void moveTo(Category newParent) {
        // 기존 부모에서 제거
        if (this.parent != null) {
            this.parent.children.remove(this);
        }
        // 새 부모에 추가
        this.parent = newParent;
        if (newParent != null) {
            newParent.children.add(this);
        }
    }

    public void moveToRoot() {
        moveTo(null);
    }

    public void changeCategoryType(CategoryType categoryType) {
        if (categoryType == null) {
            throw new IllegalArgumentException("카테고리 타입은 필수입니다.");
        }
        this.categoryType = categoryType;
    }

    public void changeSortOrder(Integer sortOrder) {
        if (sortOrder == null || sortOrder < 0) {
            throw new IllegalArgumentException("정렬 순서는 0 이상이어야 합니다.");
        }
        this.sortOrder = sortOrder;
    }

    // 루트 카테고리 여부
    public boolean isRoot() {
        return this.parent == null;
    }

    public String getName() {
        return this.categoryType.getDisplayName();
    }
}

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

    // PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 부모 카테고리 (자기참조)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    // 자식 카테고리 목록 (자기참조)
    @OneToMany(mappedBy = "parent")
    private final List<Category> children = new ArrayList<>();

    // 카테고리명
    @Column(nullable = false, length = 100)
    private String name;

    // 정렬 순서
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Builder
    public Category(Category parent,
                   String name,
                   Integer sortOrder) {
        this.parent = parent;
        this.name = name;
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

    // 루트 카테고리로 이동
    public void moveToRoot() {
        moveTo(null);
    }

    // 카테고리명 변경
    public void changeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("카테고리명은 필수입니다.");
        }
        this.name = name.trim();
    }

    // 정렬 순서 변경
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
}

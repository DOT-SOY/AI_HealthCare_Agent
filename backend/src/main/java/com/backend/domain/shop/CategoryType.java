package com.backend.domain.shop;

public enum CategoryType {
    FOOD("음식"),
    SUPPLEMENT("보충제"),
    HEALTH_GOODS("헬스용품"),
    CLOTHING("의류"),
    ETC("기타");

    private final String displayName;

    CategoryType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

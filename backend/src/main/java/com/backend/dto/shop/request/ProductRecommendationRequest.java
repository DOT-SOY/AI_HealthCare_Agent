package com.backend.dto.shop.request;

import com.backend.domain.memberinfo.MemberInfoBody.ExercisePurpose;
import com.backend.domain.shop.CategoryType;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ProductRecommendationRequest {
    private ExercisePurpose goal;
    private CategoryType productCategory;
    @Min(0)
    private BigDecimal budgetMax;
    private List<String> avoid;
    private List<String> mustHave;
    private List<String> priority;
    private String keyword;
    private String searchType;
    private String sortBy;
}


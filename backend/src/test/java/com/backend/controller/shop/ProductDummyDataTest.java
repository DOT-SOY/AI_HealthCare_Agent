package com.backend.controller.shop;

import com.backend.domain.shop.*;
import com.backend.dto.shop.request.ProductCreateRequest;
import com.backend.dto.shop.request.ProductVariantRequest;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.shop.CategoryRepository;
import com.backend.service.shop.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@SpringBootTest
@DisplayName("상품 더미 데이터 생성")
class ProductDummyDataTest {

    @Autowired ProductService productService;
    @Autowired MemberRepository memberRepository;
    @Autowired CategoryRepository categoryRepository;

    record VariantSpec(String optionText, BigDecimal price, int stockQty, boolean active) {}
    record ProductSpec(
            CategoryType categoryType,
            String name,
            String description,
            BigDecimal basePrice,
            ProductStatus status,
            List<String> imageFilePaths,
            List<VariantSpec> variants
    ) {}

    @Test
    void createDummyProducts() {
        Long adminId = 1L;
        memberRepository.findById(adminId).orElseThrow();

        String runSuffix = String.valueOf(System.currentTimeMillis());

        Long foodCategoryId = getOrCreateRootCategoryId(CategoryType.FOOD);
        Long supplementCategoryId = getOrCreateRootCategoryId(CategoryType.SUPPLEMENT);
        Long healthGoodsCategoryId = getOrCreateRootCategoryId(CategoryType.HEALTH_GOODS);
        Long clothingCategoryId = getOrCreateRootCategoryId(CategoryType.CLOTHING);
        Long etcCategoryId = getOrCreateRootCategoryId(CategoryType.ETC);

        List<ProductSpec> specs = List.of(
                // ===================== FOOD (3)
                new ProductSpec(
                        CategoryType.FOOD,
                        "닭가슴살 스테이크 100g x 10팩",
                        "고단백 저지방 식단용. 맛 옵션 제공.",
                        new BigDecimal("19800"),
                        ProductStatus.ACTIVE,
                        List.of("products/dummy/food-chicken-1.jpg"),
                        List.of(
                                new VariantSpec("flavor: original, pack: 10", new BigDecimal("19800"), 100, true),
                                new VariantSpec("flavor: spicy, pack: 10", new BigDecimal("20800"), 80, true),
                                new VariantSpec("flavor: garlic, pack: 10", new BigDecimal("20800"), 70, true)
                        )
                ),
                new ProductSpec(
                        CategoryType.FOOD,
                        "현미 곤약밥 150g x 12개",
                        "식단 관리용 저칼로리 곤약밥. 구성 옵션 제공.",
                        new BigDecimal("22800"),
                        ProductStatus.ACTIVE,
                        List.of("products/dummy/food-konjac-1.jpg"),
                        List.of(
                                new VariantSpec("type: plain, pack: 12", new BigDecimal("22800"), 120, true),
                                new VariantSpec("type: mixed-grain, pack: 12", new BigDecimal("23800"), 90, true)
                        )
                ),
                new ProductSpec(
                        CategoryType.FOOD,
                        "프로틴 오트밀 50g x 12팩",
                        "간편 아침/간식용 프로틴 오트밀. 맛 옵션 제공.",
                        new BigDecimal("26400"),
                        ProductStatus.ACTIVE,
                        List.of("products/dummy/food-oatmeal-1.jpg"),
                        List.of(
                                new VariantSpec("flavor: cocoa, pack: 12", new BigDecimal("26400"), 60, true),
                                new VariantSpec("flavor: banana, pack: 12", new BigDecimal("26400"), 60, true)
                        )
                ),

                // ===================== SUPPLEMENT (3)
                new ProductSpec(
                        CategoryType.SUPPLEMENT,
                        "옵티멈 골드 스탠다드 웨이 프로틴 2.27kg",
                        "운동 후 근육 회복을 돕는 웨이 프로틴. 맛 옵션 제공.",
                        new BigDecimal("89000"),
                        ProductStatus.ACTIVE,
                        List.of("products/dummy/supp-protein-1.jpg"),
                        List.of(
                                new VariantSpec("flavor: chocolate, size: 2.27kg", new BigDecimal("89000"), 30, true),
                                new VariantSpec("flavor: vanilla, size: 2.27kg", new BigDecimal("89000"), 25, true),
                                new VariantSpec("flavor: strawberry, size: 2.27kg", new BigDecimal("92000"), 15, true)
                        )
                ),
                new ProductSpec(
                        CategoryType.SUPPLEMENT,
                        "크레아틴 모노하이드레이트 300g",
                        "근력/파워 향상 보조. 용량 옵션 제공.",
                        new BigDecimal("24000"),
                        ProductStatus.ACTIVE,
                        List.of("products/dummy/supp-creatine-1.jpg"),
                        List.of(
                                new VariantSpec("size: 300g", new BigDecimal("24000"), 50, true),
                                new VariantSpec("size: 500g", new BigDecimal("32000"), 40, true)
                        )
                ),
                new ProductSpec(
                        CategoryType.SUPPLEMENT,
                        "BCAA 아미노산 400g",
                        "운동 중/후 회복 보조. 맛 옵션 제공.",
                        new BigDecimal("29000"),
                        ProductStatus.ACTIVE,
                        List.of("products/dummy/supp-bcaa-1.jpg"),
                        List.of(
                                new VariantSpec("flavor: lemon, size: 400g", new BigDecimal("29000"), 35, true),
                                new VariantSpec("flavor: grape, size: 400g", new BigDecimal("29000"), 35, true)
                        )
                ),

                // ===================== HEALTH_GOODS (3)
                new ProductSpec(
                        CategoryType.HEALTH_GOODS,
                        "아이언맥스 조정식 덤벨 세트",
                        "홈트에 적합한 무게 조절형 덤벨 세트. 무게 옵션 제공.",
                        new BigDecimal("125000"),
                        ProductStatus.ACTIVE,
                        List.of("products/dummy/goods-dumbbell-1.jpg"),
                        List.of(
                                new VariantSpec("weight: 10kg (pair), color: black", new BigDecimal("79000"), 20, true),
                                new VariantSpec("weight: 20kg (pair), color: black", new BigDecimal("125000"), 10, true),
                                new VariantSpec("weight: 30kg (pair), color: black", new BigDecimal("169000"), 5, true)
                        )
                ),
                new ProductSpec(
                        CategoryType.HEALTH_GOODS,
                        "케틀벨 8/12/16kg",
                        "전신 운동에 적합한 케틀벨. 무게 옵션 제공.",
                        new BigDecimal("39000"),
                        ProductStatus.ACTIVE,
                        List.of("products/dummy/goods-kettlebell-1.jpg"),
                        List.of(
                                new VariantSpec("weight: 8kg", new BigDecimal("39000"), 30, true),
                                new VariantSpec("weight: 12kg", new BigDecimal("52000"), 20, true),
                                new VariantSpec("weight: 16kg", new BigDecimal("65000"), 15, true)
                        )
                ),
                new ProductSpec(
                        CategoryType.HEALTH_GOODS,
                        "요가 매트 프리미엄 10mm",
                        "미끄럼 방지, 충격 흡수. 컬러 옵션 제공.",
                        new BigDecimal("35000"),
                        ProductStatus.ACTIVE,
                        List.of("products/dummy/goods-yogamat-1.jpg"),
                        List.of(
                                new VariantSpec("color: black, thickness: 10mm", new BigDecimal("35000"), 50, true),
                                new VariantSpec("color: blue, thickness: 10mm", new BigDecimal("35000"), 40, true)
                        )
                ),

                // ===================== CLOTHING (3)
                new ProductSpec(
                        CategoryType.CLOTHING,
                        "드라이핏 트레이닝 티셔츠",
                        "흡습속건 기능성 티셔츠. 사이즈/컬러 옵션 제공.",
                        new BigDecimal("45000"),
                        ProductStatus.ACTIVE,
                        List.of("products/dummy/cloth-shirt-1.jpg"),
                        List.of(
                                new VariantSpec("size: M, color: black", new BigDecimal("45000"), 40, true),
                                new VariantSpec("size: L, color: white", new BigDecimal("45000"), 20, true)
                        )
                ),
                new ProductSpec(
                        CategoryType.CLOTHING,
                        "트레이닝 쇼츠",
                        "가벼운 러닝/헬스용 쇼츠. 사이즈 옵션 제공.",
                        new BigDecimal("39000"),
                        ProductStatus.ACTIVE,
                        List.of("products/dummy/cloth-shorts-1.jpg"),
                        List.of(
                                new VariantSpec("size: M, color: black", new BigDecimal("39000"), 25, true),
                                new VariantSpec("size: L, color: black", new BigDecimal("39000"), 20, true)
                        )
                ),
                new ProductSpec(
                        CategoryType.CLOTHING,
                        "컴프레션 레깅스",
                        "하체 지지/압박감 제공. 사이즈 옵션 제공.",
                        new BigDecimal("52000"),
                        ProductStatus.ACTIVE,
                        List.of("products/dummy/cloth-leggings-1.jpg"),
                        List.of(
                                new VariantSpec("size: S, color: black", new BigDecimal("52000"), 15, true),
                                new VariantSpec("size: M, color: black", new BigDecimal("52000"), 20, true),
                                new VariantSpec("size: L, color: black", new BigDecimal("52000"), 15, true)
                        )
                ),

                // ===================== ETC (3)
                new ProductSpec(
                        CategoryType.ETC,
                        "폼롤러 마사지 롱타입 45cm",
                        "근막 이완 및 스트레칭용 폼롤러. 강도 옵션 제공.",
                        new BigDecimal("18000"),
                        ProductStatus.ACTIVE,
                        List.of("products/dummy/etc-foamroller-1.jpg"),
                        List.of(
                                new VariantSpec("density: soft, length: 45cm", new BigDecimal("18000"), 60, true),
                                new VariantSpec("density: hard, length: 45cm", new BigDecimal("20000"), 50, true)
                        )
                ),
                new ProductSpec(
                        CategoryType.ETC,
                        "스트랩(리프팅 스트랩)",
                        "그립 보조로 데드/로우 안정성 강화. 타입 옵션 제공.",
                        new BigDecimal("12000"),
                        ProductStatus.ACTIVE,
                        List.of("products/dummy/etc-straps-1.jpg"),
                        List.of(
                                new VariantSpec("type: cotton", new BigDecimal("12000"), 80, true),
                                new VariantSpec("type: neoprene", new BigDecimal("15000"), 60, true)
                        )
                ),
                new ProductSpec(
                        CategoryType.ETC,
                        "저항 밴드 세트 5단계",
                        "재활/근력 운동용 밴드 세트. 강도 옵션 제공.",
                        new BigDecimal("16000"),
                        ProductStatus.ACTIVE,
                        List.of("products/dummy/etc-band-1.jpg"),
                        List.of(
                                new VariantSpec("level: light (5pcs set)", new BigDecimal("16000"), 70, true),
                                new VariantSpec("level: heavy (5pcs set)", new BigDecimal("18000"), 50, true)
                        )
                )
        );

        for (ProductSpec spec : specs) {
            ProductCreateRequest req = new ProductCreateRequest();

            String uniqueName = spec.name() + " " + runSuffix;
            req.setName(uniqueName);

            req.setDescription(spec.description());
            req.setBasePrice(spec.basePrice());
            req.setStatus(spec.status());
            req.setImageFilePaths(spec.imageFilePaths());

            req.setCategoryIds(List.of(resolveCategoryId(
                    spec.categoryType(),
                    foodCategoryId, supplementCategoryId, healthGoodsCategoryId, clothingCategoryId, etcCategoryId
            )));

            List<ProductVariantRequest> variantRequests = spec.variants().stream().map(v -> {
                ProductVariantRequest vr = new ProductVariantRequest();
                vr.setOptionText(v.optionText());
                vr.setPrice(v.price());
                vr.setStockQty(v.stockQty());
                vr.setActive(v.active());
                return vr;
            }).toList();
            req.setVariants(variantRequests);

            productService.create(req, adminId);
        }
    }

    private Long resolveCategoryId(
            CategoryType type,
            Long foodId, Long supplementId, Long healthGoodsId, Long clothingId, Long etcId
    ) {
        return switch (type) {
            case FOOD -> foodId;
            case SUPPLEMENT -> supplementId;
            case HEALTH_GOODS -> healthGoodsId;
            case CLOTHING -> clothingId;
            case ETC -> etcId;
        };
    }

    /**
     * Repository 추가 없이 "List로 안전하게 수렴"
     * - findByParentIsNull(): 루트 전체 조회(List)
     * - type 필터 후 id 최소값 1개 선택
     * - 없으면 생성
     */
    private Long getOrCreateRootCategoryId(CategoryType type) {
        List<Category> roots = categoryRepository.findByParentIsNull();

        return roots.stream()
                .filter(c -> c.getCategoryType() == type)
                .min(Comparator.comparingLong(Category::getId)) // 중복이면 가장 작은 id로 수렴
                .map(Category::getId)
                .orElseGet(() -> categoryRepository.saveAndFlush(
                        Category.builder()
                                .parent(null)
                                .categoryType(type)
                                .sortOrder(0)
                                .build()
                ).getId());
    }
}

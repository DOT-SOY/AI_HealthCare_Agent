package com.backend.repository.shop;

import com.backend.domain.member.Member;
import com.backend.domain.member.MemberRole;
import com.backend.domain.shop.*;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.shop.CategoryRepository;
import com.backend.repository.shop.ProductCategoryRepository;
import com.backend.repository.shop.ProductRepository;
import com.backend.repository.shop.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@DisplayName("Product Repository 테스트")
class ProductRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Product fitnessProduct1;
    private Product fitnessProduct2;
    private Member testMember;

    @BeforeEach
    void setUp() {
        // 멤버가 이미 생성되어 있는지 확인 (멤버 생성 테스트에서 생성됨)
        // 여러 멤버가 있을 수 있으므로 첫 번째만 가져옴
        testMember = memberRepository.findAll().stream()
                .filter(m -> "test@example.com".equals(m.getEmail()))
                .findFirst()
                .orElse(null);
        
        // 멤버가 없으면 생성하고 피트니스 관련 더미 데이터 생성
        if (testMember == null) {
            testMember = getOrCreateTestMember();
        }
        // 피트니스 관련 더미 데이터 생성 (이미 있으면 재생성하지 않음)
        if (fitnessProduct1 == null || fitnessProduct2 == null) {
            createFitnessDummyData();
        }
    }

    private Member getOrCreateTestMember() {
        if (testMember == null) {
            testMember = memberRepository.findAll().stream()
                    .filter(m -> "test@example.com".equals(m.getEmail()))
                    .findFirst()
                    .orElseGet(() -> {
                        Member member = Member.builder()
                                .email("test@example.com")
                                .pw("password")
                                .name("테스트유저")
                                // .social(false)
                                .build();
                        member.addRole(MemberRole.ADMIN);
                        return memberRepository.save(member);
                    });
        }
        return testMember;
    }

    private void createFitnessDummyData() {
        Member member = getOrCreateTestMember();
        // 조정 가능 덤벨 세트
        fitnessProduct1 = Product.builder()
                .name("아이언맥스 조정 가능 덤벨 세트 20kg")
                .description("한 쌍으로 2kg부터 20kg까지 조정 가능한 프리웨이트 덤벨 세트입니다. 공간 효율적이고 다양한 근력 운동에 활용할 수 있습니다. 고품질 철제 재질과 안전한 잠금 장치로 구성되어 있습니다.")
                .status(ProductStatus.ACTIVE)
                .basePrice(new BigDecimal("89000"))
                .createdBy(member)
                .build();
        fitnessProduct1 = productRepository.save(fitnessProduct1);
        entityManager.flush();

        // 요가 매트
        fitnessProduct2 = Product.builder()
                .name("나이키 요가 매트 프리미엄 10mm")
                .description("미끄럼 방지 처리가 된 두꺼운 요가 매트입니다. 10mm 두께로 충격 흡수에 탁월하며, 요가, 필라테스, 스트레칭 등 다양한 운동에 적합합니다. 세척이 쉬운 소재로 위생적입니다.")
                .status(ProductStatus.ACTIVE)
                .basePrice(new BigDecimal("125000"))
                .createdBy(member)
                .build();
        fitnessProduct2 = productRepository.save(fitnessProduct2);
        entityManager.flush();
    }

    // ========== Member 테스트 ==========

    @Test
    @DisplayName("멤버 생성 성공")
    void createMember_Success() {
        // given
        Member member = Member.builder()
                .email("test@example.com")
                .pw("password")
                .name("테스트유저")
                // .social(false)
                .build();
        member.addRole(MemberRole.ADMIN);

        // when
        Member saved = memberRepository.save(member);
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("test@example.com");
        assertThat(saved.getName()).isEqualTo("테스트유저");
        assertThat(saved.getRoleList()).contains(MemberRole.ADMIN);
        
        // testMember에 저장하여 다른 테스트에서 사용할 수 있도록 함
        testMember = saved;
    }

    @Test
    @DisplayName("상품 저장 및 조회")
    void saveAndFind() {
        // given
        Member member = getOrCreateTestMember();
        Product product = Product.builder()
                .name("옵티멈 골드 스탠다드 휘핑 프로틴 2.27kg")
                .description("우유 단백질과 유청 단백질을 혼합한 고품질 프로틴 파우더입니다. 운동 후 근육 회복과 성장을 돕는 필수 아미노산을 함유하고 있습니다.")
                .status(ProductStatus.DRAFT)
                .basePrice(new BigDecimal("45000"))
                .createdBy(member)
                .build();

        // when
        Product saved = productRepository.save(product);
        entityManager.flush();
        entityManager.clear();

        Optional<Product> found = productRepository.findByIdAndDeletedAtIsNull(saved.getId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("옵티멈 골드 스탠다드 휘핑 프로틴 2.27kg");
        assertThat(found.get().getBasePrice()).isEqualByComparingTo(new BigDecimal("45000"));
    }

    @Test
    @DisplayName("상품명 중복 체크")
    void existsByName() {
        // given - setUp()에서 생성된 데이터 사용
        // fitnessProduct1이 없으면 생성
        if (fitnessProduct1 == null) {
            createFitnessDummyData();
        }

        // when
        boolean exists = productRepository.existsByName("아이언맥스 조정 가능 덤벨 세트 20kg");
        boolean notExists = productRepository.existsByName("존재하지 않는 상품");

        // then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("소프트 삭제된 상품은 조회되지 않음")
    void findByIdAndDeletedAtIsNull_ExcludesSoftDeleted() {
        // given - setUp()에서 생성된 데이터 사용
        if (fitnessProduct1 == null) {
            createFitnessDummyData();
        }
        Product saved = fitnessProduct1;

        // when - 소프트 삭제
        saved.softDelete();
        productRepository.save(saved);
        entityManager.flush();
        entityManager.clear();

        // then
        Optional<Product> found = productRepository.findByIdAndDeletedAtIsNull(saved.getId());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("저장된 피트니스 상품 조회 테스트")
    void findSavedFitnessProducts() {
        // given - setUp()에서 생성된 데이터
        if (fitnessProduct1 == null || fitnessProduct2 == null) {
            createFitnessDummyData();
        }

        // when
        Optional<Product> found1 = productRepository.findByIdAndDeletedAtIsNull(fitnessProduct1.getId());
        Optional<Product> found2 = productRepository.findByIdAndDeletedAtIsNull(fitnessProduct2.getId());

        // then
        assertThat(found1).isPresent();
        assertThat(found1.get().getName()).isEqualTo("아이언맥스 조정 가능 덤벨 세트 20kg");
        assertThat(found1.get().getBasePrice()).isEqualByComparingTo(new BigDecimal("89000"));
        assertThat(found1.get().getStatus()).isEqualTo(ProductStatus.ACTIVE);

        assertThat(found2).isPresent();
        assertThat(found2.get().getName()).isEqualTo("나이키 요가 매트 프리미엄 10mm");
        assertThat(found2.get().getBasePrice()).isEqualByComparingTo(new BigDecimal("125000"));
    }

    // ========== Category 테스트 ==========

    @Test
    @DisplayName("카테고리 생성 및 저장")
    void createCategory_Success() {
        // given
        Category category = Category.builder()
                .categoryType(CategoryType.HEALTH_GOODS)
                .sortOrder(1)
                .build();

        // when
        Category saved = categoryRepository.save(category);
        entityManager.flush();
        entityManager.clear();

        // then
        Optional<Category> found = categoryRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("헬스용품");
        assertThat(found.get().getCategoryType()).isEqualTo(CategoryType.HEALTH_GOODS);
        assertThat(found.get().getSortOrder()).isEqualTo(1);
        assertThat(found.get().isRoot()).isTrue();
    }

    @Test
    @DisplayName("카테고리 부모-자식 관계")
    void categoryParentChildRelationship() {
        // given
        Category parent = Category.builder()
                .categoryType(CategoryType.HEALTH_GOODS)
                .sortOrder(1)
                .build();
        parent = categoryRepository.save(parent);
        entityManager.flush();

        Category child = Category.builder()
                .parent(parent)
                .categoryType(CategoryType.HEALTH_GOODS)
                .sortOrder(1)
                .build();
        child = categoryRepository.save(child);
        entityManager.flush();
        entityManager.clear();

        // when
        Optional<Category> foundParent = categoryRepository.findById(parent.getId());
        Optional<Category> foundChild = categoryRepository.findById(child.getId());

        // then
        assertThat(foundParent).isPresent();
        assertThat(foundChild).isPresent();
        assertThat(foundChild.get().getParent()).isNotNull();
        assertThat(foundChild.get().getParent().getId()).isEqualTo(parent.getId());
        assertThat(foundChild.get().isRoot()).isFalse();
    }

    @Test
    @DisplayName("카테고리 타입 변경")
    void changeCategoryType() {
        // given
        Category category = Category.builder()
                .categoryType(CategoryType.HEALTH_GOODS)
                .sortOrder(1)
                .build();
        category = categoryRepository.save(category);
        entityManager.flush();

        // when
        category.changeCategoryType(CategoryType.SUPPLEMENT);
        categoryRepository.save(category);
        entityManager.flush();
        entityManager.clear();

        // then
        Optional<Category> found = categoryRepository.findById(category.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getCategoryType()).isEqualTo(CategoryType.SUPPLEMENT);
        assertThat(found.get().getName()).isEqualTo("보충제");
    }

    @Test
    @DisplayName("카테고리 부모 변경")
    void moveCategory() {
        // given
        Category parent1 = Category.builder()
                .categoryType(CategoryType.HEALTH_GOODS)
                .sortOrder(1)
                .build();
        parent1 = categoryRepository.save(parent1);
        entityManager.flush();

        Category parent2 = Category.builder()
                .categoryType(CategoryType.SUPPLEMENT)
                .sortOrder(2)
                .build();
        parent2 = categoryRepository.save(parent2);
        entityManager.flush();

        Category child = Category.builder()
                .parent(parent1)
                .categoryType(CategoryType.HEALTH_GOODS)
                .sortOrder(1)
                .build();
        child = categoryRepository.save(child);
        entityManager.flush();

        // when
        child.moveTo(parent2);
        categoryRepository.save(child);
        entityManager.flush();
        entityManager.clear();

        // then
        Optional<Category> found = categoryRepository.findById(child.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getParent().getId()).isEqualTo(parent2.getId());
    }

    @Test
    @DisplayName("카테고리를 루트로 이동")
    void moveCategoryToRoot() {
        // given
        Category parent = Category.builder()
                .categoryType(CategoryType.HEALTH_GOODS)
                .sortOrder(1)
                .build();
        parent = categoryRepository.save(parent);
        entityManager.flush();

        Category child = Category.builder()
                .parent(parent)
                .categoryType(CategoryType.HEALTH_GOODS)
                .sortOrder(1)
                .build();
        child = categoryRepository.save(child);
        entityManager.flush();

        // when
        child.moveToRoot();
        categoryRepository.save(child);
        entityManager.flush();
        entityManager.clear();

        // then
        Optional<Category> found = categoryRepository.findById(child.getId());
        assertThat(found).isPresent();
        assertThat(found.get().isRoot()).isTrue();
        assertThat(found.get().getParent()).isNull();
    }

    // ========== ProductVariant 테스트 ==========

    @Test
    @DisplayName("상품 Variant 생성 및 저장")
    void createProductVariant_Success() {
        // given
        if (fitnessProduct1 == null) {
            createFitnessDummyData();
        }
        Product product = fitnessProduct1;
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku("DUMBBELL-20KG-001")
                .optionText("weight: 20kg, color: black")
                .price(new BigDecimal("89000"))
                .stockQty(10)
                .active(true)
                .build();

        // when
        ProductVariant saved = productVariantRepository.save(variant);
        entityManager.flush();
        entityManager.clear();

        // then
        Optional<ProductVariant> found = productVariantRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getSku()).isEqualTo("DUMBBELL-20KG-001");
        assertThat(found.get().getPrice()).isEqualByComparingTo(new BigDecimal("89000"));
        assertThat(found.get().getStockQty()).isEqualTo(10);
        assertThat(found.get().isActive()).isTrue();
    }

    @Test
    @DisplayName("Variant 가격이 null일 때 상품 기본 가격 사용")
    void variantResolvePrice_UseBasePrice() {
        // given
        if (fitnessProduct1 == null) {
            createFitnessDummyData();
        }
        Product product = fitnessProduct1; // basePrice = 89000
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku("DUMBBELL-20KG-002")
                .optionText("weight: 20kg")
                .price(null) // 가격이 null
                .stockQty(5)
                .build();
        variant = productVariantRepository.save(variant);
        entityManager.flush();
        entityManager.clear();

        // when
        Optional<ProductVariant> found = productVariantRepository.findById(variant.getId());
        BigDecimal resolvedPrice = found.get().resolvePrice();

        // then
        assertThat(found).isPresent();
        assertThat(resolvedPrice).isEqualByComparingTo(new BigDecimal("89000"));
    }

    @Test
    @DisplayName("Variant 재고 수량 변경")
    void updateVariantStock() {
        // given
        if (fitnessProduct1 == null) {
            createFitnessDummyData();
        }
        Product product = fitnessProduct1;
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku("DUMBBELL-20KG-003")
                .optionText("weight: 20kg")
                .stockQty(10)
                .build();
        variant = productVariantRepository.save(variant);
        entityManager.flush();

        // when
        variant.updateStock(20);
        productVariantRepository.save(variant);
        entityManager.flush();
        entityManager.clear();

        // then
        Optional<ProductVariant> found = productVariantRepository.findById(variant.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStockQty()).isEqualTo(20);
    }

    @Test
    @DisplayName("Variant 재고 증가")
    void increaseVariantStock() {
        // given
        if (fitnessProduct1 == null) {
            createFitnessDummyData();
        }
        Product product = fitnessProduct1;
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku("DUMBBELL-20KG-004")
                .optionText("weight: 20kg")
                .stockQty(10)
                .build();
        variant = productVariantRepository.save(variant);
        entityManager.flush();

        // when
        variant.increaseStock(5);
        productVariantRepository.save(variant);
        entityManager.flush();
        entityManager.clear();

        // then
        Optional<ProductVariant> found = productVariantRepository.findById(variant.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStockQty()).isEqualTo(15);
    }

    @Test
    @DisplayName("Variant 재고 감소")
    void decreaseVariantStock() {
        // given
        if (fitnessProduct1 == null) {
            createFitnessDummyData();
        }
        Product product = fitnessProduct1;
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku("DUMBBELL-20KG-005")
                .optionText("weight: 20kg")
                .stockQty(10)
                .build();
        variant = productVariantRepository.save(variant);
        entityManager.flush();

        // when
        variant.decreaseStock(3);
        productVariantRepository.save(variant);
        entityManager.flush();
        entityManager.clear();

        // then
        Optional<ProductVariant> found = productVariantRepository.findById(variant.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStockQty()).isEqualTo(7);
    }

    @Test
    @DisplayName("Variant 재고 감소 실패 - 재고 부족")
    void decreaseVariantStock_InsufficientStock() {
        // given
        if (fitnessProduct1 == null) {
            createFitnessDummyData();
        }
        Product product = fitnessProduct1;
        final ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku("DUMBBELL-20KG-006")
                .optionText("weight: 20kg")
                .stockQty(5)
                .build();
        productVariantRepository.save(variant);

        // when & then
        assertThatThrownBy(() -> variant.decreaseStock(10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("재고가 부족합니다");
    }

    @Test
    @DisplayName("상품 ID로 Variant 목록 조회")
    void findVariantsByProductId() {
        // given
        if (fitnessProduct1 == null) {
            createFitnessDummyData();
        }
        Product product = fitnessProduct1;
        ProductVariant variant1 = ProductVariant.builder()
                .product(product)
                .sku("DUMBBELL-20KG-007")
                .optionText("weight: 20kg, color: black")
                .stockQty(10)
                .build();
        ProductVariant variant2 = ProductVariant.builder()
                .product(product)
                .sku("DUMBBELL-20KG-008")
                .optionText("weight: 20kg, color: red")
                .stockQty(5)
                .build();
        productVariantRepository.save(variant1);
        productVariantRepository.save(variant2);
        entityManager.flush();
        entityManager.clear();

        // when
        List<ProductVariant> variants = productVariantRepository.findByProductId(product.getId());

        // then
        assertThat(variants).hasSizeGreaterThanOrEqualTo(2);
        assertThat(variants).extracting(ProductVariant::getSku)
                .contains("DUMBBELL-20KG-007", "DUMBBELL-20KG-008");
    }

    @Test
    @DisplayName("SKU로 Variant 조회")
    void findVariantBySku() {
        // given
        if (fitnessProduct1 == null) {
            createFitnessDummyData();
        }
        Product product = fitnessProduct1;
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku("DUMBBELL-20KG-009")
                .optionText("weight: 20kg")
                .stockQty(10)
                .build();
        variant = productVariantRepository.save(variant);
        entityManager.flush();
        entityManager.clear();

        // when
        Optional<ProductVariant> found = productVariantRepository.findBySku("DUMBBELL-20KG-009");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(variant.getId());
    }

    // ========== Product와 Category 연결 테스트 ==========

    @Test
    @DisplayName("상품에 카테고리 연결")
    void linkProductToCategory() {
        // given
        if (fitnessProduct1 == null) {
            createFitnessDummyData();
        }
        Product product = fitnessProduct1;
        Category category = Category.builder()
                .categoryType(CategoryType.HEALTH_GOODS)
                .sortOrder(1)
                .build();
        category = categoryRepository.save(category);
        entityManager.flush();

        // when
        ProductCategory productCategory = ProductCategory.builder()
                .product(product)
                .category(category)
                .build();
        productCategoryRepository.save(productCategory);
        entityManager.flush();
        entityManager.clear();

        // then
        List<ProductCategory> productCategories = productCategoryRepository.findById_ProductId(product.getId());
        assertThat(productCategories).hasSize(1);
        assertThat(productCategories.get(0).getCategory().getName()).isEqualTo("헬스용품");
        assertThat(productCategories.get(0).getCategory().getCategoryType()).isEqualTo(CategoryType.HEALTH_GOODS);
    }

    @Test
    @DisplayName("카테고리로 연결된 상품 조회")
    void findProductsByCategory() {
        // given
        if (fitnessProduct1 == null || fitnessProduct2 == null) {
            createFitnessDummyData();
        }
        Product product1 = fitnessProduct1;
        Product product2 = fitnessProduct2;
        Category category = Category.builder()
                .categoryType(CategoryType.HEALTH_GOODS)
                .sortOrder(1)
                .build();
        category = categoryRepository.save(category);
        entityManager.flush();

        ProductCategory pc1 = ProductCategory.builder()
                .product(product1)
                .category(category)
                .build();
        ProductCategory pc2 = ProductCategory.builder()
                .product(product2)
                .category(category)
                .build();
        productCategoryRepository.save(pc1);
        productCategoryRepository.save(pc2);
        entityManager.flush();
        entityManager.clear();

        // when
        List<ProductCategory> productCategories = productCategoryRepository.findByCategoryId(category.getId());

        // then
        assertThat(productCategories).hasSize(2);
        assertThat(productCategories).extracting(pc -> pc.getProduct().getName())
                .contains("아이언맥스 조정 가능 덤벨 세트 20kg", "나이키 요가 매트 프리미엄 10mm");
    }
}

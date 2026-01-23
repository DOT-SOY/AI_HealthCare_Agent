package com.backend.service.shop;

import com.backend.common.dto.PageRequest;
import com.backend.common.dto.PageResponse;
import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.domain.member.Member;
import com.backend.domain.member.MemberRole;
import com.backend.domain.shop.*;
import com.backend.dto.shop.request.ProductCreateRequest;
import com.backend.dto.shop.request.ProductSearchRequest;
import com.backend.dto.shop.request.ProductUpdateRequest;
import com.backend.dto.shop.response.ProductResponse;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.shop.CategoryRepository;
import com.backend.repository.shop.ProductCategoryRepository;
import com.backend.repository.shop.ProductRepository;
import com.backend.repository.shop.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayName("Product Service 테스트")
class ProductServiceTest {

    @Autowired
    private ProductService productService;

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
    private Category fitnessCategory;
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
        if (fitnessProduct1 == null || fitnessProduct2 == null || fitnessCategory == null) {
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
                                .nickname("테스트유저")
                                .social(false)
                                .build();
                        member.addRole(MemberRole.ADMIN);
                        return memberRepository.save(member);
                    });
        }
        return testMember;
    }

    private void createFitnessDummyData() {
        Member member = getOrCreateTestMember();
        // 카테고리 생성
        fitnessCategory = Category.builder()
                .categoryType(CategoryType.HEALTH_GOODS)
                .sortOrder(1)
                .build();
        fitnessCategory = categoryRepository.save(fitnessCategory);

        // 조정 가능 덤벨 세트
        fitnessProduct1 = Product.builder()
                .name("아이언맥스 조정 가능 덤벨 세트 20kg")
                .description("한 쌍으로 2kg부터 20kg까지 조정 가능한 프리웨이트 덤벨 세트입니다. 공간 효율적이고 다양한 근력 운동에 활용할 수 있습니다. 고품질 철제 재질과 안전한 잠금 장치로 구성되어 있습니다.")
                .status(ProductStatus.ACTIVE)
                .basePrice(new BigDecimal("89000"))
                .createdBy(member)
                .build();
        fitnessProduct1 = productRepository.save(fitnessProduct1);

        // 상품에 카테고리 연결
        ProductCategory productCategory1 = ProductCategory.builder()
                .product(fitnessProduct1)
                .category(fitnessCategory)
                .build();
        productCategoryRepository.save(productCategory1);

        // 덤벨 Variant 추가
        ProductVariant variant1 = ProductVariant.builder()
                .product(fitnessProduct1)
                .sku("DUMBBELL-20KG-BLACK")
                .optionJson("{\"weight\":\"20kg\",\"color\":\"black\"}")
                .price(new BigDecimal("89000"))
                .stockQty(10)
                .active(true)
                .build();
        productVariantRepository.save(variant1);

        ProductVariant variant2 = ProductVariant.builder()
                .product(fitnessProduct1)
                .sku("DUMBBELL-20KG-RED")
                .optionJson("{\"weight\":\"20kg\",\"color\":\"red\"}")
                .price(new BigDecimal("95000"))
                .stockQty(5)
                .active(true)
                .build();
        productVariantRepository.save(variant2);

        // 요가 매트
        fitnessProduct2 = Product.builder()
                .name("나이키 요가 매트 프리미엄 10mm")
                .description("미끄럼 방지 처리가 된 두꺼운 요가 매트입니다. 10mm 두께로 충격 흡수에 탁월하며, 요가, 필라테스, 스트레칭 등 다양한 운동에 적합합니다. 세척이 쉬운 소재로 위생적입니다.")
                .status(ProductStatus.ACTIVE)
                .basePrice(new BigDecimal("125000"))
                .createdBy(member)
                .build();
        fitnessProduct2 = productRepository.save(fitnessProduct2);

        // 상품에 카테고리 연결
        ProductCategory productCategory2 = ProductCategory.builder()
                .product(fitnessProduct2)
                .category(fitnessCategory)
                .build();
        productCategoryRepository.save(productCategory2);
    }

    // ========== Member 테스트 ==========

    @Test
    @DisplayName("멤버 생성 성공")
    void createMember_Success() {
        // given
        Member member = Member.builder()
                .email("test@example.com")
                .pw("password")
                .nickname("테스트유저")
                .social(false)
                .build();
        member.addRole(MemberRole.ADMIN);

        // when
        Member saved = memberRepository.save(member);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("test@example.com");
        assertThat(saved.getNickname()).isEqualTo("테스트유저");
        assertThat(saved.getRoleList()).contains(MemberRole.ADMIN);
        
        // testMember에 저장하여 다른 테스트에서 사용할 수 있도록 함
        testMember = saved;
    }

    // ========== Product 테스트 ==========

    @Test
    @DisplayName("상품 등록 성공")
    void createProduct_Success() {
        // given - 멤버가 생성되어 있어야 함
        if (testMember == null) {
            // 멤버가 없으면 생성
            testMember = memberRepository.findByEmail("test@example.com")
                    .orElseGet(() -> {
                        Member member = Member.builder()
                                .email("test@example.com")
                                .pw("password")
                                .nickname("테스트유저")
                                .social(false)
                                .build();
                        member.addRole(MemberRole.ADMIN);
                        return memberRepository.save(member);
                    });
        }
        
        // 고유한 이름 사용 (이전 테스트 데이터와 충돌 방지)
        String uniqueName = "옵티멈 골드 스탠다드 휘핑 프로틴 2.27kg " + System.currentTimeMillis();
        ProductCreateRequest request = new ProductCreateRequest();
        request.setName(uniqueName);
        request.setDescription("우유 단백질과 유청 단백질을 혼합한 고품질 프로틴 파우더입니다. 운동 후 근육 회복과 성장을 돕는 필수 아미노산을 함유하고 있습니다.");
        request.setBasePrice(new BigDecimal("45000"));

        // when
        Member member = getOrCreateTestMember();
        ProductResponse result = productService.create(request, member.getId());

        // then
        assertThat(result.getId()).isNotNull();
        assertThat(result.getName()).isEqualTo(uniqueName);
        assertThat(result.getBasePrice()).isEqualByComparingTo(new BigDecimal("45000"));
        assertThat(result.getStatus()).isEqualTo(ProductStatus.DRAFT);
    }

    @Test
    @DisplayName("상품 등록 실패 - 중복된 이름")
    void createProduct_DuplicateName() {
        // given - setUp()에서 생성된 fitnessProduct1과 동일한 이름
        if (fitnessProduct1 == null) {
            createFitnessDummyData();
        }
        ProductCreateRequest request = new ProductCreateRequest();
        request.setName("아이언맥스 조정 가능 덤벨 세트 20kg");
        request.setDescription("설명");
        request.setBasePrice(new BigDecimal("10000"));

        // when & then
        Member member = getOrCreateTestMember();
        assertThatThrownBy(() -> productService.create(request, member.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SHOP_PRODUCT_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("상품 단건 조회 성공")
    void findById_Success() {
        // given - setUp()에서 생성된 데이터
        if (fitnessProduct1 == null) {
            createFitnessDummyData();
        }

        // when
        ProductResponse result = productService.findById(fitnessProduct1.getId());

        // then
        assertThat(result.getId()).isEqualTo(fitnessProduct1.getId());
        assertThat(result.getName()).isEqualTo("아이언맥스 조정 가능 덤벨 세트 20kg");
        assertThat(result.getBasePrice()).isEqualByComparingTo(new BigDecimal("89000"));
        assertThat(result.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    @DisplayName("상품 단건 조회 실패 - 존재하지 않는 상품")
    void findById_NotFound() {
        // given
        Long nonExistentId = 999999L;

        // when & then
        assertThatThrownBy(() -> productService.findById(nonExistentId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SHOP_PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("상품 리스트 조회 성공")
    void findAll_Success() {
        // given
        if (fitnessProduct1 == null) {
            createFitnessDummyData();
        }
        PageRequest pageRequest = new PageRequest();
        pageRequest.setPage(1);
        pageRequest.setPageSize(20);

        ProductSearchRequest searchRequest = new ProductSearchRequest();

        // when
        PageResponse<ProductResponse> result = productService.findAll(pageRequest, searchRequest);

        // then
        assertThat(result.getItems()).isNotEmpty();
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(20);
        // setUp()에서 생성된 상품들이 포함되어 있는지 확인
        assertThat(result.getItems().stream()
                .anyMatch(p -> p.getName().equals("아이언맥스 조정 가능 덤벨 세트 20kg"))).isTrue();
    }

    @Test
    @DisplayName("상품 수정 성공")
    void updateProduct_Success() {
        // given - setUp()에서 생성된 데이터 사용, 고유한 이름 사용 (이전 테스트 데이터와 충돌 방지)
        if (fitnessProduct1 == null) {
            createFitnessDummyData();
        }
        String uniqueName = "아이언맥스 조정 가능 덤벨 세트 20kg 업그레이드 " + System.currentTimeMillis();
        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setName(uniqueName);
        request.setBasePrice(new BigDecimal("95000"));

        // when
        ProductResponse result = productService.update(fitnessProduct1.getId(), request);

        // then
        assertThat(result.getName()).isEqualTo(uniqueName);
        assertThat(result.getBasePrice()).isEqualByComparingTo(new BigDecimal("95000"));
        
        // 실제 DB에서도 확인
        Product updated = productRepository.findByIdAndDeletedAtIsNull(fitnessProduct1.getId())
                .orElseThrow();
        assertThat(updated.getName()).isEqualTo(uniqueName);
        assertThat(updated.getBasePrice()).isEqualByComparingTo(new BigDecimal("95000"));
    }

    @Test
    @DisplayName("상품 수정 실패 - 존재하지 않는 상품")
    void updateProduct_NotFound() {
        // given
        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setName("수정된 상품명");
        Long nonExistentId = 999999L;

        // when & then
        assertThatThrownBy(() -> productService.update(nonExistentId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SHOP_PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("상품 삭제 성공")
    void deleteProduct_Success() {
        // given - setUp()에서 생성된 데이터 사용
        if (fitnessProduct2 == null) {
            createFitnessDummyData();
        }
        Long productId = fitnessProduct2.getId();

        // when
        productService.delete(productId);

        // then - 소프트 삭제되었는지 확인
        assertThat(productRepository.findByIdAndDeletedAtIsNull(productId)).isEmpty();
        // 실제로는 삭제되지 않고 deletedAt만 설정됨
        assertThat(productRepository.findById(productId)).isPresent();
    }

    @Test
    @DisplayName("상품 삭제 실패 - 존재하지 않는 상품")
    void deleteProduct_NotFound() {
        // given
        Long nonExistentId = 999999L;

        // when & then
        assertThatThrownBy(() -> productService.delete(nonExistentId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SHOP_PRODUCT_NOT_FOUND);
    }

    // ========== Category 필터링 테스트 ==========

    @Test
    @DisplayName("카테고리로 상품 검색")
    void findAll_WithCategoryFilter() {
        // given
        if (fitnessCategory == null) {
            createFitnessDummyData();
        }
        PageRequest pageRequest = new PageRequest();
        pageRequest.setPage(1);
        pageRequest.setPageSize(20);

        ProductSearchRequest searchRequest = new ProductSearchRequest();
        searchRequest.setCategoryId(fitnessCategory.getId());

        // when
        PageResponse<ProductResponse> result = productService.findAll(pageRequest, searchRequest);

        // then
        assertThat(result.getItems()).isNotEmpty();
        // 카테고리로 필터링된 상품들이 포함되어 있는지 확인
        assertThat(result.getItems().stream()
                .anyMatch(p -> p.getName().equals("아이언맥스 조정 가능 덤벨 세트 20kg"))).isTrue();
        assertThat(result.getItems().stream()
                .anyMatch(p -> p.getName().equals("나이키 요가 매트 프리미엄 10mm"))).isTrue();
    }

    // ========== ProductVariant 테스트 ==========

    @Test
    @DisplayName("상품에 Variant 추가 후 조회")
    @Transactional
    void productWithVariants() {
        // given
        if (fitnessProduct1 == null) {
            createFitnessDummyData();
        }
        Product product = fitnessProduct1;

        // when - Product 엔티티를 다시 조회하여 variants 확인
        Product found = productRepository.findByIdAndDeletedAtIsNull(product.getId())
                .orElseThrow();

        // then - variants가 제대로 로드되는지 확인
        // LAZY 로딩이므로 명시적으로 접근해야 함
        // @BatchSize가 적용되어 있으므로 배치로 로딩됨
        List<ProductVariant> variants = found.getVariants();
        assertThat(variants).isNotEmpty();
        // setUp에서 최소 2개의 variant를 생성했으므로 2개 이상이어야 함
        assertThat(variants.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Variant 재고 관리 - 재고 감소")
    void variantStockManagement_Decrease() {
        // given
        if (fitnessProduct1 == null) {
            createFitnessDummyData();
        }
        Product product = fitnessProduct1;
        // 여러 개가 있을 수 있으므로 첫 번째만 가져옴
        ProductVariant variant = productVariantRepository.findAll().stream()
                .filter(v -> "DUMBBELL-20KG-BLACK".equals(v.getSku()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("DUMBBELL-20KG-BLACK variant를 찾을 수 없습니다"));
        int originalStock = variant.getStockQty();

        // when
        variant.decreaseStock(3);
        productVariantRepository.save(variant);

        // then
        ProductVariant updated = productVariantRepository.findById(variant.getId())
                .orElseThrow();
        assertThat(updated.getStockQty()).isEqualTo(originalStock - 3);
    }

    @Test
    @DisplayName("Variant 재고 관리 - 재고 증가")
    void variantStockManagement_Increase() {
        // given
        if (fitnessProduct1 == null) {
            createFitnessDummyData();
        }
        // 여러 개가 있을 수 있으므로 첫 번째만 가져옴
        ProductVariant variant = productVariantRepository.findAll().stream()
                .filter(v -> "DUMBBELL-20KG-BLACK".equals(v.getSku()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("DUMBBELL-20KG-BLACK variant를 찾을 수 없습니다"));
        int originalStock = variant.getStockQty();

        // when
        variant.increaseStock(5);
        productVariantRepository.save(variant);

        // then
        ProductVariant updated = productVariantRepository.findById(variant.getId())
                .orElseThrow();
        assertThat(updated.getStockQty()).isEqualTo(originalStock + 5);
    }

    @Test
    @DisplayName("Variant 가격 해석 - variant 가격이 null인 경우")
    void variantResolvePrice_WhenVariantPriceIsNull() {
        // given
        if (fitnessProduct1 == null) {
            createFitnessDummyData();
        }
        Product product = fitnessProduct1; // basePrice = 89000
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku("DUMBBELL-20KG-NO-PRICE")
                .optionJson("{\"weight\":\"20kg\"}")
                .price(null) // variant 가격이 null
                .stockQty(10)
                .build();
        variant = productVariantRepository.save(variant);

        // when
        BigDecimal resolvedPrice = variant.resolvePrice();

        // then
        assertThat(resolvedPrice).isEqualByComparingTo(new BigDecimal("89000"));
    }

    @Test
    @DisplayName("Variant 가격 해석 - variant 가격이 있는 경우")
    void variantResolvePrice_WhenVariantPriceExists() {
        // given
        if (fitnessProduct1 == null) {
            createFitnessDummyData();
        }
        Product product = fitnessProduct1; // basePrice = 89000
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku("DUMBBELL-20KG-WITH-PRICE")
                .optionJson("{\"weight\":\"20kg\"}")
                .price(new BigDecimal("95000")) // variant 가격이 있음
                .stockQty(10)
                .build();
        variant = productVariantRepository.save(variant);

        // when
        BigDecimal resolvedPrice = variant.resolvePrice();

        // then
        assertThat(resolvedPrice).isEqualByComparingTo(new BigDecimal("95000"));
    }

    @Test
    @DisplayName("상품의 모든 활성 Variant 조회")
    void findActiveVariantsByProduct() {
        // given
        if (fitnessProduct1 == null) {
            createFitnessDummyData();
        }
        Product product = fitnessProduct1;
        // 비활성 Variant 추가
        ProductVariant inactiveVariant = ProductVariant.builder()
                .product(product)
                .sku("DUMBBELL-20KG-INACTIVE")
                .optionJson("{\"weight\":\"20kg\",\"color\":\"blue\"}")
                .stockQty(0)
                .active(false)
                .build();
        productVariantRepository.save(inactiveVariant);

        // when
        List<ProductVariant> activeVariants = productVariantRepository.findByActiveTrue();

        // then
        assertThat(activeVariants).isNotEmpty();
        assertThat(activeVariants).allMatch(ProductVariant::isActive);
    }
}

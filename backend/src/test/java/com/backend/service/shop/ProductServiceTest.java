package com.backend.service.shop;

import com.backend.common.dto.PageRequest;
import com.backend.common.dto.PageResponse;
import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.domain.shop.*;
import com.backend.dto.shop.request.ProductCreateRequest;
import com.backend.dto.shop.request.ProductSearchRequest;
import com.backend.dto.shop.request.ProductUpdateRequest;
import com.backend.dto.shop.response.ProductResponse;
import com.backend.repository.shop.products.CategoryRepository;
import com.backend.repository.shop.products.ProductCategoryRepository;
import com.backend.repository.shop.products.ProductRepository;
import com.backend.repository.shop.products.ProductVariantRepository;
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

    private Product fitnessProduct1;
    private Product fitnessProduct2;
    private Category fitnessCategory;

    @BeforeEach
    void setUp() {
        // 피트니스 관련 더미 데이터 생성 및 실제 DB에 저장
        createFitnessDummyData();
    }

    private void createFitnessDummyData() {
        // 카테고리 생성
        fitnessCategory = Category.builder()
                .name("피트니스 용품")
                .sortOrder(1)
                .build();
        fitnessCategory = categoryRepository.save(fitnessCategory);

        // 조정 가능 덤벨 세트
        fitnessProduct1 = Product.builder()
                .name("아이언맥스 조정 가능 덤벨 세트 20kg")
                .description("한 쌍으로 2kg부터 20kg까지 조정 가능한 프리웨이트 덤벨 세트입니다. 공간 효율적이고 다양한 근력 운동에 활용할 수 있습니다. 고품질 철제 재질과 안전한 잠금 장치로 구성되어 있습니다.")
                .status(ProductStatus.ACTIVE)
                .basePrice(new BigDecimal("89000"))
                .createdBy(1L)
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
                .createdBy(1L)
                .build();
        fitnessProduct2 = productRepository.save(fitnessProduct2);

        // 상품에 카테고리 연결
        ProductCategory productCategory2 = ProductCategory.builder()
                .product(fitnessProduct2)
                .category(fitnessCategory)
                .build();
        productCategoryRepository.save(productCategory2);
    }

    @Test
    @DisplayName("상품 등록 성공")
    void createProduct_Success() {
        // given - 고유한 이름 사용 (이전 테스트 데이터와 충돌 방지)
        String uniqueName = "옵티멈 골드 스탠다드 휘핑 프로틴 2.27kg " + System.currentTimeMillis();
        ProductCreateRequest request = new ProductCreateRequest();
        request.setName(uniqueName);
        request.setDescription("우유 단백질과 유청 단백질을 혼합한 고품질 프로틴 파우더입니다. 운동 후 근육 회복과 성장을 돕는 필수 아미노산을 함유하고 있습니다.");
        request.setBasePrice(new BigDecimal("45000"));

        // when
        ProductResponse result = productService.create(request, 1L);

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
        ProductCreateRequest request = new ProductCreateRequest();
        request.setName("아이언맥스 조정 가능 덤벨 세트 20kg");
        request.setDescription("설명");
        request.setBasePrice(new BigDecimal("10000"));

        // when & then
        assertThatThrownBy(() -> productService.create(request, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("상품 단건 조회 성공")
    void findById_Success() {
        // given - setUp()에서 생성된 데이터

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
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("상품 리스트 조회 성공")
    void findAll_Success() {
        // given
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
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("상품 삭제 성공")
    void deleteProduct_Success() {
        // given - setUp()에서 생성된 데이터 사용
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
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    // ========== Category 필터링 테스트 ==========

    @Test
    @DisplayName("카테고리로 상품 검색")
    void findAll_WithCategoryFilter() {
        // given
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
        Product product = fitnessProduct1;

        // when - Product 엔티티를 다시 조회하여 variants 확인
        Product found = productRepository.findByIdAndDeletedAtIsNull(product.getId())
                .orElseThrow();

        // then - variants가 제대로 로드되는지 확인
        // LAZY 로딩이므로 명시적으로 접근해야 함
        assertThat(found.getVariants()).isNotEmpty();
        assertThat(found.getVariants().size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Variant 재고 관리 - 재고 감소")
    void variantStockManagement_Decrease() {
        // given
        Product product = fitnessProduct1;
        ProductVariant variant = productVariantRepository.findBySku("DUMBBELL-20KG-BLACK")
                .orElseThrow();
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
        ProductVariant variant = productVariantRepository.findBySku("DUMBBELL-20KG-BLACK")
                .orElseThrow();
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

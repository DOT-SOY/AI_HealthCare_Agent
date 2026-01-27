//package com.backend.controller.shop;
//
//import com.backend.domain.member.Member;
//import com.backend.domain.member.MemberRole;
//import com.backend.domain.shop.*;
//import com.backend.dto.shop.request.ProductCreateRequest;
//import com.backend.dto.shop.request.ProductUpdateRequest;
//import com.backend.repository.member.MemberRepository;
//import com.backend.repository.shop.CategoryRepository;
//import com.backend.repository.shop.ProductCategoryRepository;
//import com.backend.repository.shop.ProductRepository;
//import com.backend.repository.shop.ProductVariantRepository;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.http.MediaType;
//import org.springframework.test.web.servlet.MockMvc;
//
//import java.math.BigDecimal;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
//import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
//
//@SpringBootTest
//@AutoConfigureMockMvc
//@DisplayName("Product Controller 테스트")
//class ProductControllerTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    @Autowired
//    private ProductRepository productRepository;
//
//    @Autowired
//    private CategoryRepository categoryRepository;
//
//    @Autowired
//    private ProductCategoryRepository productCategoryRepository;
//
//    @Autowired
//    private ProductVariantRepository productVariantRepository;
//
//    @Autowired
//    private MemberRepository memberRepository;
//
//    private Product fitnessProduct1;
//    private Product fitnessProduct2;
//    private Category fitnessCategory;
//    private Member testMember;
//
//    @BeforeEach
//    void setUp() {
//        // 멤버가 이미 생성되어 있는지 확인 (멤버 생성 테스트에서 생성됨)
//        // 여러 멤버가 있을 수 있으므로 첫 번째만 가져옴
//        testMember = memberRepository.findAll().stream()
//                .filter(m -> "test@example.com".equals(m.getEmail()))
//                .findFirst()
//                .orElse(null);
//
//        // 멤버가 없으면 생성하고 피트니스 관련 더미 데이터 생성
//        if (testMember == null) {
//            testMember = getOrCreateTestMember();
//        }
//        // 피트니스 관련 더미 데이터 생성 (이미 있으면 재생성하지 않음)
//        if (fitnessProduct1 == null || fitnessProduct2 == null || fitnessCategory == null) {
//            createFitnessDummyData();
//        }
//    }
//
//    private Member getOrCreateTestMember() {
//        if (testMember == null) {
//            testMember = memberRepository.findAll().stream()
//                    .filter(m -> "test@example.com".equals(m.getEmail()))
//                    .findFirst()
//                    .orElseGet(() -> {
//                        Member member = Member.builder()
//                                .email("test@example.com")
//                                .pw("password")
//                                .nickname("테스트유저")
//                                .social(false)
//                                .build();
//                        member.addRole(MemberRole.ADMIN);
//                        return memberRepository.save(member);
//                    });
//        }
//        return testMember;
//    }
//
//    private void createFitnessDummyData() {
//        Member member = getOrCreateTestMember();
//        // 카테고리 생성
//        fitnessCategory = Category.builder()
//                .categoryType(CategoryType.HEALTH_GOODS)
//                .sortOrder(1)
//                .build();
//        fitnessCategory = categoryRepository.save(fitnessCategory);
//
//        // 조정 가능 덤벨 세트
//        fitnessProduct1 = Product.builder()
//                .name("아이언맥스 조정 가능 덤벨 세트 20kg")
//                .description("한 쌍으로 2kg부터 20kg까지 조정 가능한 프리웨이트 덤벨 세트입니다. 공간 효율적이고 다양한 근력 운동에 활용할 수 있습니다. 고품질 철제 재질과 안전한 잠금 장치로 구성되어 있습니다.")
//                .status(ProductStatus.ACTIVE)
//                .basePrice(new BigDecimal("89000"))
//                .createdBy(member)
//                .build();
//        fitnessProduct1 = productRepository.save(fitnessProduct1);
//
//        // 상품에 카테고리 연결
//        ProductCategory productCategory1 = ProductCategory.builder()
//                .product(fitnessProduct1)
//                .category(fitnessCategory)
//                .build();
//        productCategoryRepository.save(productCategory1);
//
//        // 덤벨 Variant 추가
//        ProductVariant variant1 = ProductVariant.builder()
//                .product(fitnessProduct1)
//                .sku("DUMBBELL-20KG-BLACK")
//                .optionJson("{\"weight\":\"20kg\",\"color\":\"black\"}")
//                .price(new BigDecimal("89000"))
//                .stockQty(10)
//                .active(true)
//                .build();
//        productVariantRepository.save(variant1);
//
//        // 요가 매트
//        fitnessProduct2 = Product.builder()
//                .name("나이키 요가 매트 프리미엄 10mm")
//                .description("미끄럼 방지 처리가 된 두꺼운 요가 매트입니다. 10mm 두께로 충격 흡수에 탁월하며, 요가, 필라테스, 스트레칭 등 다양한 운동에 적합합니다. 세척이 쉬운 소재로 위생적입니다.")
//                .status(ProductStatus.ACTIVE)
//                .basePrice(new BigDecimal("125000"))
//                .createdBy(member)
//                .build();
//        fitnessProduct2 = productRepository.save(fitnessProduct2);
//
//        // 상품에 카테고리 연결
//        ProductCategory productCategory2 = ProductCategory.builder()
//                .product(fitnessProduct2)
//                .category(fitnessCategory)
//                .build();
//        productCategoryRepository.save(productCategory2);
//    }
//
//    // ========== Member 테스트 ==========
//
//    @Test
//    @DisplayName("멤버 생성 성공")
//    void createMember_Success() {
//        // given
//        Member member = Member.builder()
//                .email("test@example.com")
//                .pw("password")
//                .nickname("테스트유저")
//                .social(false)
//                .build();
//        member.addRole(MemberRole.ADMIN);
//
//        // when
//        Member saved = memberRepository.save(member);
//
//        // then
//        assertThat(saved.getId()).isNotNull();
//        assertThat(saved.getEmail()).isEqualTo("test@example.com");
//        assertThat(saved.getNickname()).isEqualTo("테스트유저");
//        assertThat(saved.getRoleList()).contains(MemberRole.ADMIN);
//
//        // testMember에 저장하여 다른 테스트에서 사용할 수 있도록 함
//        testMember = saved;
//    }
//
//    // ========== Product 테스트 ==========
//
//    @Test
//    @DisplayName("상품 등록 성공")
//    void createProduct_Success() throws Exception {
//        // given - 멤버가 생성되어 있어야 함
//        if (testMember == null) {
//            // 멤버가 없으면 생성
//            testMember = getOrCreateTestMember();
//        }
//
//        // given - 고유한 이름 사용 (이전 테스트 데이터와 충돌 방지)
//        String uniqueName = "옵티멈 골드 스탠다드 휘핑 프로틴 2.27kg " + System.currentTimeMillis();
//        ProductCreateRequest request = new ProductCreateRequest();
//        request.setName(uniqueName);
//        request.setDescription("우유 단백질과 유청 단백질을 혼합한 고품질 프로틴 파우더입니다. 운동 후 근육 회복과 성장을 돕는 필수 아미노산을 함유하고 있습니다.");
//        request.setBasePrice(new BigDecimal("45000"));
//
//        // when & then
//        mockMvc.perform(post("/api/v1/products")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andDo(print())
//                .andExpect(status().isCreated())
//                .andExpect(header().exists("Location"))
//                .andExpect(jsonPath("$.id").exists())
//                .andExpect(jsonPath("$.name").value(uniqueName))
//                .andExpect(jsonPath("$.basePrice").value(45000))
//                .andExpect(jsonPath("$.status").value("DRAFT"));
//    }
//
//    @Test
//    @DisplayName("상품 등록 실패 - 검증 오류")
//    void createProduct_ValidationError() throws Exception {
//        // given
//        ProductCreateRequest request = new ProductCreateRequest();
//        // name이 비어있음
//
//        // when & then
//        mockMvc.perform(post("/api/v1/products")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andDo(print())
//                .andExpect(status().isUnprocessableEntity());
//    }
//
//    @Test
//    @DisplayName("상품 단건 조회 성공")
//    void findById_Success() throws Exception {
//        // given - setUp()에서 생성된 데이터 사용
//        if (fitnessProduct1 == null) {
//            createFitnessDummyData();
//        }
//
//        // when & then
//        mockMvc.perform(get("/api/v1/products/" + fitnessProduct1.getId()))
//                .andDo(print())
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.id").value(fitnessProduct1.getId()))
//                .andExpect(jsonPath("$.name").value("아이언맥스 조정 가능 덤벨 세트 20kg"))
//                .andExpect(jsonPath("$.basePrice").value(89000))
//                .andExpect(jsonPath("$.status").value("ACTIVE"));
//    }
//
//    @Test
//    @DisplayName("상품 리스트 조회 성공")
//    void findAll_Success() throws Exception {
//        // given - setUp()에서 생성된 데이터 사용
//
//        // when & then
//        mockMvc.perform(get("/api/v1/products")
//                        .param("page", "1")
//                        .param("page_size", "20"))
//                .andDo(print())
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.items").isArray())
//                .andExpect(jsonPath("$.items.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
//                .andExpect(jsonPath("$.page").value(1))
//                .andExpect(jsonPath("$.page_size").value(20))
//                .andExpect(jsonPath("$.total").exists());
//    }
//
//    @Test
//    @DisplayName("상품 리스트 조회 - 검색 필터 적용")
//    void findAll_WithSearchFilter() throws Exception {
//        // given - setUp()에서 생성된 데이터 사용
//        // fitnessProduct1: "아이언맥스 조정 가능 덤벨 세트 20kg", 가격 89000 (조건에 맞음)
//        // fitnessProduct2: "나이키 요가 매트 프리미엄 10mm", 가격 125000 (조건에 맞음)
//
//        // when & then - 덤벨 키워드로 검색 (가격 범위: 50000~150000)
//        mockMvc.perform(get("/api/v1/products")
//                        .param("page", "1")
//                        .param("page_size", "20")
//                        .param("keyword", "덤벨")
//                        .param("status", "ACTIVE")
//                        .param("minPrice", "50000")
//                        .param("maxPrice", "150000")
//                        .param("sortBy", "basePrice")
//                        .param("direction", "ASC"))
//                .andDo(print())
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.items").isArray())
//                .andExpect(jsonPath("$.items.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
//    }
//
//    @Test
//    @DisplayName("상품 수정 성공")
//    void updateProduct_Success() throws Exception {
//        // given - setUp()에서 생성된 데이터 사용, 고유한 이름 사용 (이전 테스트 데이터와 충돌 방지)
//        if (fitnessProduct1 == null) {
//            createFitnessDummyData();
//        }
//        String uniqueName = "아이언맥스 조정 가능 덤벨 세트 20kg 업그레이드 " + System.currentTimeMillis();
//        ProductUpdateRequest request = new ProductUpdateRequest();
//        request.setName(uniqueName);
//        request.setBasePrice(new BigDecimal("95000"));
//
//        // when & then
//        mockMvc.perform(patch("/api/v1/products/" + fitnessProduct1.getId())
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(request)))
//                .andDo(print())
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.id").value(fitnessProduct1.getId()))
//                .andExpect(jsonPath("$.name").value(uniqueName))
//                .andExpect(jsonPath("$.basePrice").value(95000));
//    }
//
//    @Test
//    @DisplayName("상품 삭제 성공")
//    void deleteProduct_Success() throws Exception {
//        // given - setUp()에서 생성된 데이터 사용
//        if (fitnessProduct2 == null) {
//            createFitnessDummyData();
//        }
//        Long productId = fitnessProduct2.getId();
//
//        // when & then
//        mockMvc.perform(delete("/api/v1/products/" + productId))
//                .andDo(print())
//                .andExpect(status().isNoContent());
//
//        // 삭제 후 조회 시 404가 나와야 함 (소프트 삭제)
//        mockMvc.perform(get("/api/v1/products/" + productId))
//                .andDo(print())
//                .andExpect(status().isNotFound());
//    }
//
//    @Test
//    @DisplayName("상품 리스트 조회 - 카테고리 필터 적용")
//    void findAll_WithCategoryFilter() throws Exception {
//        // given - setUp()에서 생성된 데이터 사용
//        // fitnessProduct1과 fitnessProduct2는 모두 fitnessCategory에 연결됨
//
//        // when & then - 카테고리 필터로 검색
//        mockMvc.perform(get("/api/v1/products")
//                        .param("page", "1")
//                        .param("page_size", "20")
//                        .param("categoryId", String.valueOf(fitnessCategory.getId())))
//                .andDo(print())
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.items").isArray())
//                .andExpect(jsonPath("$.items.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
//    }
//
//    @Test
//    @DisplayName("상품 리스트 조회 - 카테고리와 키워드 필터 조합")
//    void findAll_WithCategoryAndKeyword() throws Exception {
//        // given - setUp()에서 생성된 데이터 사용
//
//        // when & then - 카테고리와 키워드 필터 조합
//        mockMvc.perform(get("/api/v1/products")
//                        .param("page", "1")
//                        .param("page_size", "20")
//                        .param("categoryId", String.valueOf(fitnessCategory.getId()))
//                        .param("keyword", "덤벨"))
//                .andDo(print())
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.items").isArray())
//                .andExpect(jsonPath("$.items.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
//                .andExpect(jsonPath("$.items[0].name").value(org.hamcrest.Matchers.containsString("덤벨")));
//    }
//}

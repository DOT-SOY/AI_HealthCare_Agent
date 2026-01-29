package com.backend.service.shop;

import com.backend.common.dto.PageRequest;
import com.backend.common.dto.PageResponse;
import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.domain.member.Member;
import com.backend.domain.shop.*;
import com.backend.dto.shop.request.ProductCreateRequest;
import com.backend.dto.shop.request.ProductSearchRequest;
import com.backend.dto.shop.request.ProductUpdateRequest;
import com.backend.dto.shop.request.ProductVariantRequest;
import com.backend.dto.shop.response.CategoryResponse;
import com.backend.dto.shop.response.ProductImageResponse;
import com.backend.dto.shop.response.ProductResponse;
import com.backend.dto.shop.response.ProductVariantResponse;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.shop.*;
import com.backend.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductSearch productSearch;
    private final MemberRepository memberRepository;
    private final FileStorageService fileStorageService;

    private final ProductImageRepository productImageRepository;
    private final ProductVariantRepository productVariantRepository;

    private final CategoryRepository categoryRepository;
    private final ProductCategoryRepository productCategoryRepository;

    // ===========================
    // Public APIs
    // ===========================

    @Override
    @Transactional
    public ProductResponse create(ProductCreateRequest request, Long createdBy) {
        if (productRepository.existsByName(request.getName())) {
            throw new BusinessException(ErrorCode.SHOP_PRODUCT_ALREADY_EXISTS);
        }

        Member member = memberRepository.findById(createdBy != null ? createdBy : 1L)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, createdBy));

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .basePrice(request.getBasePrice())
                .status(request.getStatus())
                .createdBy(member)
                .build();

        // 1) 이미지/옵션은 컬렉션 기반으로 구성 (cascade로 저장)
        replaceImages(product, request.getImageFilePaths());
        replaceVariants(product, request.getVariants());

        // 2) 상품 저장(이미지/옵션 포함)
        Product saved = productRepository.save(product);
        log.info("Product created: id={}, name={}", saved.getId(), saved.getName());

        // 3) 카테고리는 조인 테이블이라 별도로 replace
        List<Long> categoryIds = resolveCategoryIds(request.getCategoryTypes(), request.getCategoryIds());
        if (categoryIds != null) {
            replaceCategories(saved, categoryIds);
        }

        // 응답용 조회(현재 구조 유지)
        List<ProductImage> images = productImageRepository.findByProductIdAndDeletedAtIsNull(saved.getId());
        List<ProductVariant> variants = productVariantRepository.findByProductId(saved.getId());
        return toFullResponse(saved, images, variants);
    }

    @Override
    public ProductResponse findById(Long id) {
        Product product = findActiveProduct(id);

        List<ProductImage> images = productImageRepository.findByProductIdAndDeletedAtIsNull(id);
        List<ProductVariant> variants = productVariantRepository.findByProductId(id);

        return toFullResponse(product, images, variants);
    }

    @Override
    public PageResponse<ProductResponse> findAll(PageRequest pageRequest, ProductSearchRequest searchRequest) {
        Page<Product> products = productSearch.search(searchRequest.toCondition(), pageRequest.toPageable());
        List<Product> content = products.getContent();

        if (content.isEmpty()) {
            return PageResponse.of(
                    products.map(p -> toResponseWithImagesAndVariants(p, List.of(), List.of())),
                    pageRequest.getPage()
            );
        }

        List<Long> productIds = content.stream().map(Product::getId).toList();

        List<ProductImage> allImages = productImageRepository.findByProductIdIn(productIds);
        List<ProductVariant> allVariants = productVariantRepository.findByProductIdIn(productIds);

        Map<Long, List<ProductImage>> imagesByProductId =
                allImages.stream().collect(Collectors.groupingBy(img -> img.getProduct().getId()));

        Map<Long, List<ProductVariant>> variantsByProductId =
                allVariants.stream().collect(Collectors.groupingBy(v -> v.getProduct().getId()));

        List<ProductResponse> responses = content.stream()
                .map(p -> toResponseWithImagesAndVariants(
                        p,
                        imagesByProductId.getOrDefault(p.getId(), List.of()),
                        variantsByProductId.getOrDefault(p.getId(), List.of())
                ))
                .toList();

        Page<ProductResponse> responsePage = PageableExecutionUtils.getPage(
                responses,
                products.getPageable(),
                products::getTotalElements
        );

        return PageResponse.of(responsePage, pageRequest.getPage());
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest request) {
        Product product = findActiveProduct(id);

        applyBasicFields(product, request);

        if (request.getImageFilePaths() != null) {
            // update는 diff 기반 replace 권장(동일 filePath 재사용)
            replaceImagesDiff(product, request.getImageFilePaths());
        }

        if (request.getVariants() != null) {
            replaceVariants(product, request.getVariants());
        }

        List<Long> categoryIdsToUse = resolveCategoryIds(request.getCategoryTypes(), request.getCategoryIds());
        if (categoryIdsToUse != null) {
            replaceCategories(product, categoryIdsToUse);
        }

        Product updated = productRepository.save(product);
        log.info("Product updated: id={}", updated.getId());

        List<ProductImage> images = productImageRepository.findByProductIdAndDeletedAtIsNull(updated.getId());
        List<ProductVariant> variants = productVariantRepository.findByProductId(updated.getId());
        return toFullResponse(updated, images, variants);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Product product = findActiveProduct(id);
        product.softDelete();
        productRepository.save(product);
        log.info("Product deleted: id={}", id);
    }

    /**
     * 단건 토글(대표 지정)은 patch 성격이므로 유지.
     */
    @Transactional
    public void setPrimaryImage(Long productId, UUID imageUuid) {
        Product product = findActiveProduct(productId);

        ProductImage target = product.getImages().stream()
                .filter(img -> img.getDeletedAt() == null && img.getUuid().equals(imageUuid))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("이미지를 찾을 수 없습니다. productId=" + productId + ", imageUuid=" + imageUuid));

        // 활성 이미지 primary 정리 후 target만 primary
        product.getImages().stream()
                .filter(img -> img.getDeletedAt() == null)
                .forEach(ProductImage::markAsSecondary);

        target.markAsPrimary();

        productRepository.save(product);
        log.info("Primary image set: productId={}, imageUuid={}", productId, imageUuid);
    }

    // ===========================
    // Core Replace Methods
    // ===========================

    /**
     * create 등에서 단순 구성용(기존이 없다는 가정) - 빈이면 전체 제거 의미.
     */
    private void replaceImages(Product product, List<String> filePaths) {
        List<String> requested = normalizePaths(filePaths);
        if (requested.isEmpty()) {
            // 활성만 soft delete + 컬렉션에서 제거(현재 정책 유지)
            softDeleteAndRemoveActiveImages(product);
            return;
        }

        softDeleteAndRemoveActiveImages(product);

        for (int i = 0; i < requested.size(); i++) {
            product.getImages().add(ProductImage.builder()
                    .product(product)
                    .filePath(requested.get(i))
                    .primaryImage(i == 0)
                    .build());
        }
    }

    /**
     * update에서 권장: 동일 filePath는 재사용, 없는 것만 생성.
     */
    private void replaceImagesDiff(Product product, List<String> filePaths) {
        List<String> requested = normalizePaths(filePaths);

        if (requested.isEmpty()) {
            softDeleteAndRemoveActiveImages(product);
            log.info("All images removed from product: productId={}", product.getId());
            return;
        }

        // 기존 활성 이미지: filePath -> image
        Map<String, ProductImage> activeByPath = product.getImages().stream()
                .filter(img -> img.getDeletedAt() == null)
                .collect(Collectors.toMap(
                        ProductImage::getFilePath,
                        Function.identity(),
                        (a, b) -> a
                ));

        // 요청 순서대로 재사용/생성
        List<ProductImage> nextActives = new ArrayList<>(requested.size());
        for (String path : requested) {
            ProductImage reused = activeByPath.remove(path);
            if (reused != null) {
                reused.markAsSecondary(); // primary는 아래에서 1개 보장
                nextActives.add(reused);
            } else {
                nextActives.add(ProductImage.builder()
                        .product(product)
                        .filePath(path)
                        .primaryImage(false)
                        .build());
            }
        }

        // 요청에 없는 기존 활성 => soft delete + 컬렉션에서 제거
        for (ProductImage toDelete : activeByPath.values()) {
            toDelete.softDelete();
        }
        product.getImages().removeAll(activeByPath.values());

        // 현재 활성들을 제거하고 nextActives로 재구성(중복 add 방지 목적)
        List<ProductImage> currentActives = product.getImages().stream()
                .filter(img -> img.getDeletedAt() == null)
                .toList();
        product.getImages().removeAll(currentActives);
        product.getImages().addAll(nextActives);

        // primary 1개 보장: 요청 첫 번째를 primary
        product.getImages().stream()
                .filter(img -> img.getDeletedAt() == null)
                .forEach(ProductImage::markAsSecondary);

        product.getImages().stream()
                .filter(img -> img.getDeletedAt() == null)
                .findFirst()
                .ifPresent(ProductImage::markAsPrimary);

        log.info("Product images updated(diff): productId={}, requested={}, keptOrCreated={}, deleted={}",
                product.getId(), requested.size(), nextActives.size(), activeByPath.size());
    }

    private void softDeleteAndRemoveActiveImages(Product product) {
        List<ProductImage> actives = product.getImages().stream()
                .filter(img -> img.getDeletedAt() == null)
                .toList();
        actives.forEach(ProductImage::softDelete);
        product.getImages().removeAll(actives);
    }

    private void replaceVariants(Product product, List<ProductVariantRequest> requests) {
        if (requests == null) return;

        product.getVariants().clear();

        if (requests.isEmpty()) {
            log.info("All variants removed from product: productId={}", product.getId());
            return;
        }

        for (ProductVariantRequest req : requests) {
            product.getVariants().add(ProductVariant.builder()
                    .product(product)
                    .optionText(req.getOptionText())
                    .price(req.getPrice())
                    .stockQty(req.getStockQty() != null ? req.getStockQty() : 0)
                    .active(req.getActive() != null ? req.getActive() : true)
                    .build());
        }

        log.info("Product variants replaced: productId={}, variantCount={}", product.getId(), requests.size());
    }

    /**
     * 카테고리는 현재 구조상 repo 기반 유지(조인 엔티티).
     * - update/create 둘 다 replace로 통일
     */
    @Transactional
    private void replaceCategories(Product product, List<Long> categoryIds) {
        // 기존 연결 삭제
        List<ProductCategory> existing = productCategoryRepository.findById_ProductId(product.getId());
        if (!existing.isEmpty()) {
            // deleteAll이 반복 delete로 갈 수 있어 필요시 deleteAllInBatch로 교체
            productCategoryRepository.deleteAll(existing);
        }

        if (categoryIds == null || categoryIds.isEmpty()) {
            log.info("All categories removed from product: productId={}", product.getId());
            return;
        }

        // 새 연결 생성
        List<ProductCategory> toSave = new ArrayList<>(categoryIds.size());
        for (Long categoryId : categoryIds) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_CATEGORY_NOT_FOUND, categoryId));

            toSave.add(ProductCategory.builder()
                    .product(product)
                    .category(category)
                    .build());
        }

        productCategoryRepository.saveAll(toSave);
        log.info("Product categories replaced: productId={}, categoryCount={}", product.getId(), categoryIds.size());
    }

    // ===========================
    // Helpers
    // ===========================

    private Product findActiveProduct(Long id) {
        return productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_PRODUCT_NOT_FOUND, id));
    }

    private void applyBasicFields(Product product, ProductUpdateRequest request) {
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            String newName = request.getName().trim();
            if (!product.getName().equals(newName)) {
                productRepository.findByNameAndDeletedAtIsNull(newName)
                        .filter(p -> !p.getId().equals(product.getId()))
                        .ifPresent(p -> { throw new BusinessException(ErrorCode.SHOP_PRODUCT_ALREADY_EXISTS); });
            }
            product.changeName(newName);
        }

        if (request.getDescription() != null && !request.getDescription().trim().isEmpty()) {
            product.changeDescription(request.getDescription());
        }

        if (request.getBasePrice() != null) {
            product.changeBasePrice(request.getBasePrice());
        }

        if (request.getStatus() != null) {
            product.changeStatus(request.getStatus());
        }
    }

    private List<String> normalizePaths(List<String> paths) {
        if (paths == null) return List.of();
        return paths.stream()
                .filter(p -> p != null && !p.trim().isEmpty())
                .map(String::trim)
                .distinct() // 요청 중복 방지(순서 유지)
                .toList();
    }

    /**
     * categoryTypes(Enum 이름) 또는 categoryIds를 카테고리 ID 목록으로 반환.
     * - null: 유지
     * - empty: 전체 제거 의미(상위에서 replaceCategories 호출)
     */
    private List<Long> resolveCategoryIds(List<String> categoryTypes, List<Long> categoryIds) {
        if (categoryTypes != null && !categoryTypes.isEmpty()) {
            List<Long> ids = new ArrayList<>();
            for (String typeName : categoryTypes) {
                CategoryType type;
                try {
                    type = CategoryType.valueOf(typeName);
                } catch (IllegalArgumentException e) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
                }
                Category category = getOrCreateRootCategory(type);
                ids.add(category.getId());
            }
            return ids;
        }
        return categoryIds;
    }

    @Transactional
    private Category getOrCreateRootCategory(CategoryType categoryType) {
        return categoryRepository.findByCategoryTypeAndParentIsNull(categoryType)
                .orElseGet(() -> {
                    log.info("Creating root category for type: {} (Lazy Create)", categoryType);
                    Category saved = categoryRepository.saveAndFlush(Category.builder()
                            .parent(null)
                            .categoryType(categoryType)
                            .sortOrder(0)
                            .build());
                    return categoryRepository.findByCategoryTypeAndParentIsNull(categoryType).orElse(saved);
                });
    }

    // ===========================
    // Response mapping (기존 구조 유지)
    // ===========================

    private ProductResponse toFullResponse(Product product, List<ProductImage> images, List<ProductVariant> variants) {
        ProductResponse base = toResponseWithImages(product, images);

        List<ProductVariantResponse> variantResponses = variants.stream()
                .map(ProductVariantResponse::from)
                .toList();

        List<ProductCategory> pcs = productCategoryRepository.findById_ProductId(product.getId());
        List<CategoryResponse> categoryResponses = pcs.stream()
                .map(pc -> CategoryResponse.from(pc.getCategory()))
                .toList();

        return ProductResponse.builder()
                .id(base.getId())
                .name(base.getName())
                .description(base.getDescription())
                .status(base.getStatus())
                .basePrice(base.getBasePrice())
                .createdAt(base.getCreatedAt())
                .updatedAt(base.getUpdatedAt())
                .createdBy(base.getCreatedBy())
                .images(base.getImages())
                .variants(variantResponses)
                .categories(categoryResponses)
                .build();
    }

    private ProductResponse toResponseWithImages(Product product, List<ProductImage> images) {
        ProductResponse base = ProductResponse.from(product);

        List<ProductImageResponse> imageResponses = images.stream()
                .sorted(Comparator.comparing(ProductImage::getCreatedAt))
                .map(this::toImageResponse)
                .toList();

        return ProductResponse.builder()
                .id(base.getId())
                .name(base.getName())
                .description(base.getDescription())
                .status(base.getStatus())
                .basePrice(base.getBasePrice())
                .createdAt(base.getCreatedAt())
                .updatedAt(base.getUpdatedAt())
                .createdBy(base.getCreatedBy())
                .images(imageResponses)
                .build();
    }

    private ProductResponse toResponseWithImagesAndVariants(Product product, List<ProductImage> images, List<ProductVariant> variants) {
        ProductResponse base = toResponseWithImages(product, images);

        List<ProductVariantResponse> variantResponses = variants.stream()
                .map(ProductVariantResponse::from)
                .toList();

        return ProductResponse.builder()
                .id(base.getId())
                .name(base.getName())
                .description(base.getDescription())
                .status(base.getStatus())
                .basePrice(base.getBasePrice())
                .createdAt(base.getCreatedAt())
                .updatedAt(base.getUpdatedAt())
                .createdBy(base.getCreatedBy())
                .images(base.getImages())
                .variants(variantResponses)
                .build();
    }

    private ProductImageResponse toImageResponse(ProductImage image) {
        String filePath = image.getFilePath();
        String url = (filePath != null && !filePath.trim().isEmpty())
                ? fileStorageService.getFileUrl(filePath)
                : null;

        return ProductImageResponse.builder()
                .uuid(image.getUuid())
                .url(url)
                .filePath(filePath)
                .primaryImage(image.isPrimaryImage())
                .build();
    }
}

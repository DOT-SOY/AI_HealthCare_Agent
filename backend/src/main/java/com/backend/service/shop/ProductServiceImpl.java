package com.backend.service.shop;

import com.backend.common.dto.PageRequest;
import com.backend.common.dto.PageResponse;
import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.domain.member.Member;
import com.backend.domain.shop.Category;
import com.backend.domain.shop.CategoryType;
import com.backend.domain.shop.Product;
import com.backend.domain.shop.ProductCategory;
import com.backend.domain.shop.ProductImage;
import com.backend.domain.shop.ProductVariant;
import com.backend.dto.shop.request.ProductCreateRequest;
import com.backend.dto.shop.request.ProductSearchRequest;
import com.backend.dto.shop.request.ProductUpdateRequest;
import com.backend.dto.shop.request.ProductVariantRequest;
import com.backend.dto.shop.response.CategoryResponse;
import com.backend.dto.shop.response.ProductImageResponse;
import com.backend.dto.shop.response.ProductResponse;
import com.backend.dto.shop.response.ProductVariantResponse;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.shop.CategoryRepository;
import com.backend.repository.shop.ProductCategoryRepository;
import com.backend.repository.shop.ProductImageRepository;
import com.backend.repository.shop.ProductRepository;
import com.backend.repository.shop.ProductSearch;
import com.backend.repository.shop.ProductVariantRepository;
import com.backend.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    @Override
    @Transactional
    public ProductResponse create(ProductCreateRequest request, Long createdBy) {
        // TODO: 추후 member 도메인 추가 시 admin 권한 체크
        // if (!isAdmin(createdBy)) {
        //     throw new BusinessException(ErrorCode.FORBIDDEN);
        // }

        // 중복 체크
        if (productRepository.existsByName(request.getName())) {
            throw new BusinessException(ErrorCode.SHOP_PRODUCT_ALREADY_EXISTS);
        }

        // Member 조회
        Member member = memberRepository.findById(createdBy != null ? createdBy : 1L)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, createdBy));

        // 도메인 엔티티 생성
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .basePrice(request.getBasePrice())
                .status(request.getStatus()) // null이면 기본값 DRAFT로 설정됨
                .createdBy(member)
                .build();

        // 저장
        Product saved = productRepository.save(product);
        log.info("Product created: id={}, name={}", saved.getId(), saved.getName());

        // 이미지 연결 (imageFilePaths가 있으면)
        if (request.getImageFilePaths() != null && !request.getImageFilePaths().isEmpty()) {
            connectImagesToProduct(saved.getId(), request.getImageFilePaths());
        }

        // Variants 연결 (variants가 있으면)
        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            connectVariantsToProduct(saved, request.getVariants());
        }

        // Categories 연결 (categoryTypes 우선, 없으면 categoryIds)
        List<Long> categoryIdsToUse = resolveCategoryIds(request.getCategoryTypes(), request.getCategoryIds());
        if (categoryIdsToUse != null && !categoryIdsToUse.isEmpty()) {
            connectCategoriesToProduct(saved, categoryIdsToUse);
        }

        // DTO 변환 및 이미지 URL 조립 (images와 variants를 별도로 조회)
        List<ProductImage> images = productImageRepository.findByProductId(saved.getId());
        List<ProductVariant> variants = productVariantRepository.findByProductId(saved.getId());
        return toFullResponse(saved, images, variants);
    }

    @Override
    public ProductResponse findById(Long id) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_PRODUCT_NOT_FOUND, id));

        // images와 variants를 별도로 조회 (MultipleBagFetchException 방지)
        List<ProductImage> images = productImageRepository.findByProductId(id);
        List<ProductVariant> variants = productVariantRepository.findByProductId(id);

        return toFullResponse(product, images, variants);
    }

    @Override
    public PageResponse<ProductResponse> findAll(PageRequest pageRequest, ProductSearchRequest searchRequest) {
        // 1. Products 페이징 조회 (createdBy만 페치 조인, images는 제외)
        Page<Product> products = productSearch.search(
                searchRequest.toCondition(),
                pageRequest.toPageable()
        );

        // 2-쿼리 전략: product ids로 images와 variants를 한 번에 조회
        List<Product> productList = products.getContent();
        if (productList.isEmpty()) {
            return PageResponse.of(
                    products.map(p -> toResponseWithImagesAndVariants(p, java.util.Collections.emptyList(), java.util.Collections.emptyList())),
                    pageRequest.getPage()
            );
        }

        // 2. Product IDs 추출
        List<Long> productIds = productList.stream()
                .map(Product::getId)
                .collect(Collectors.toList());

        // 3. 모든 images를 한 번에 조회 (1번의 쿼리)
        List<ProductImage> allImages = productImageRepository.findByProductIdIn(productIds);

        // 4. 모든 variants를 한 번에 조회 (1번의 쿼리)
        List<ProductVariant> allVariants = productVariantRepository.findByProductIdIn(productIds);

        // 5. Product ID별로 images와 variants 그룹화
        Map<Long, List<ProductImage>> imagesByProductId = allImages.stream()
                .collect(Collectors.groupingBy(image -> image.getProduct().getId()));
        
        Map<Long, List<ProductVariant>> variantsByProductId = allVariants.stream()
                .collect(Collectors.groupingBy(variant -> variant.getProduct().getId()));

        // 6. 각 Product에 images와 variants 설정 후 변환
        List<ProductResponse> responses = productList.stream()
                .map(product -> {
                    // Product에 images와 variants 설정 (영속성 컨텍스트에 이미 로드된 데이터 사용)
                    List<ProductImage> productImages = imagesByProductId.getOrDefault(
                            product.getId(), 
                            java.util.Collections.emptyList()
                    );
                    List<ProductVariant> productVariants = variantsByProductId.getOrDefault(
                            product.getId(),
                            java.util.Collections.emptyList()
                    );
                    // images와 variants를 product에 설정 (프록시 대신 실제 컬렉션 사용)
                    return toResponseWithImagesAndVariants(product, productImages, productVariants);
                })
                .collect(Collectors.toList());

        // Page 객체 재생성 (변환된 responses 사용)
        Page<ProductResponse> responsePage = org.springframework.data.support.PageableExecutionUtils.getPage(
                responses,
                products.getPageable(),
                products::getTotalElements
        );

        return PageResponse.of(
                responsePage,
                pageRequest.getPage()
        );
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest request) {
        // TODO: 추후 member 도메인 추가 시 admin 권한 체크
        // if (!isAdmin(getCurrentUserId())) {
        //     throw new BusinessException(ErrorCode.FORBIDDEN);
        // }

        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_PRODUCT_NOT_FOUND, id));

        // 부분 업데이트 (null이 아닌 필드만 업데이트)
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            String newName = request.getName().trim();
            // 이름이 실제로 변경된 경우에만 중복 체크 (성능 최적화)
            if (!product.getName().equals(newName)) {
                // 이름 중복 체크 (다른 상품과 이름이 겹치지 않는지)
                productRepository.findByNameAndDeletedAtIsNull(newName)
                        .filter(p -> !p.getId().equals(id))
                        .ifPresent(p -> {
                            throw new BusinessException(ErrorCode.SHOP_PRODUCT_ALREADY_EXISTS);
                        });
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

        // 이미지 업데이트 (imageFilePaths가 null이 아니면 처리)
        if (request.getImageFilePaths() != null) {
            updateProductImages(product, request.getImageFilePaths());
        }

        // Variants 업데이트 (variants가 null이 아니면 처리)
        if (request.getVariants() != null) {
            updateProductVariants(product, request.getVariants());
        }

        // Categories 업데이트 (categoryTypes 우선, 없으면 categoryIds)
        List<Long> categoryIdsToUse = resolveCategoryIds(request.getCategoryTypes(), request.getCategoryIds());
        if (categoryIdsToUse != null) {
            updateProductCategories(product, categoryIdsToUse);
        }

        Product updated = productRepository.save(product);
        log.info("Product updated: id={}", updated.getId());

        // images와 variants를 별도로 조회 (MultipleBagFetchException 방지)
        List<ProductImage> images = productImageRepository.findByProductId(updated.getId());
        List<ProductVariant> variants = productVariantRepository.findByProductId(updated.getId());
        return toFullResponse(updated, images, variants);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        // TODO: 추후 member 도메인 추가 시 admin 권한 체크
        // if (!isAdmin(getCurrentUserId())) {
        //     throw new BusinessException(ErrorCode.FORBIDDEN);
        // }

        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_PRODUCT_NOT_FOUND, id));

        // 소프트 삭제
        product.softDelete();
        productRepository.save(product);

        log.info("Product deleted: id={}", id);
    }


    /**
     * Product 엔티티를 ProductResponse로 변환하고 variants와 categories를 포함합니다.
     * 
     * @param product Product 엔티티
     * @param images 이미 조회된 ProductImage 리스트
     * @param variants 이미 조회된 ProductVariant 리스트
     * @return 완전한 ProductResponse
     */
    private ProductResponse toFullResponse(Product product, List<ProductImage> images, List<ProductVariant> variants) {
        ProductResponse response = toResponseWithImages(product, images);
        
        // Variants 변환
        List<ProductVariantResponse> variantResponses = variants.stream()
                .map(ProductVariantResponse::from)
                .collect(Collectors.toList());
        
        // Categories 변환
        List<ProductCategory> productCategories = productCategoryRepository.findById_ProductId(product.getId());
        List<CategoryResponse> categoryResponses = productCategories.stream()
                .map(pc -> CategoryResponse.from(pc.getCategory()))
                .collect(Collectors.toList());
        
        return ProductResponse.builder()
                .id(response.getId())
                .name(response.getName())
                .description(response.getDescription())
                .status(response.getStatus())
                .basePrice(response.getBasePrice())
                .createdAt(response.getCreatedAt())
                .updatedAt(response.getUpdatedAt())
                .createdBy(response.getCreatedBy())
                .images(response.getImages())
                .variants(variantResponses)
                .categories(categoryResponses)
                .build();
    }

    /**
     * Product 엔티티를 ProductResponse로 변환하고 이미지 URL을 조립합니다.
     * 2-쿼리 전략에서 사용 (이미 조회된 images를 전달)
     * 
     * @param product Product 엔티티
     * @param images 이미 조회된 ProductImage 리스트 (null이면 product.getImages() 사용)
     * @return 이미지 URL이 조립된 ProductResponse
     */
    private ProductResponse toResponseWithImages(Product product, List<ProductImage> images) {
        ProductResponse response = ProductResponse.from(product);
        
        // 이미지 목록을 ProductImageResponse로 변환
        List<ProductImageResponse> imageResponses = images.stream()
                .sorted(Comparator.comparing(ProductImage::getCreatedAt))
                .map(this::toImageResponse)
                .collect(Collectors.toList());
        
        // Builder를 사용하여 images 필드 설정
        return ProductResponse.builder()
                .id(response.getId())
                .name(response.getName())
                .description(response.getDescription())
                .status(response.getStatus())
                .basePrice(response.getBasePrice())
                .createdAt(response.getCreatedAt())
                .updatedAt(response.getUpdatedAt())
                .createdBy(response.getCreatedBy())
                .images(imageResponses)
                .build();
    }

    /**
     * Product 엔티티를 ProductResponse로 변환하고 이미지 URL과 variants를 조립합니다.
     * 2-쿼리 전략에서 사용 (이미 조회된 images와 variants를 전달)
     * 
     * @param product Product 엔티티
     * @param images 이미 조회된 ProductImage 리스트
     * @param variants 이미 조회된 ProductVariant 리스트
     * @return 이미지 URL과 variants가 조립된 ProductResponse
     */
    private ProductResponse toResponseWithImagesAndVariants(Product product, List<ProductImage> images, List<ProductVariant> variants) {
        ProductResponse response = toResponseWithImages(product, images);
        
        // Variants 변환
        List<ProductVariantResponse> variantResponses = variants.stream()
                .map(ProductVariantResponse::from)
                .collect(Collectors.toList());
        
        // Builder를 사용하여 variants 필드 설정
        return ProductResponse.builder()
                .id(response.getId())
                .name(response.getName())
                .description(response.getDescription())
                .status(response.getStatus())
                .basePrice(response.getBasePrice())
                .createdAt(response.getCreatedAt())
                .updatedAt(response.getUpdatedAt())
                .createdBy(response.getCreatedBy())
                .images(response.getImages())
                .variants(variantResponses)
                .build();
    }

    /**
     * ProductImage 엔티티를 ProductImageResponse로 변환하고 URL을 조립합니다.
     *
     * @param image ProductImage 엔티티
     * @return URL이 조립된 ProductImageResponse
     */
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

    /**
     * 상품의 대표 이미지를 설정합니다.
     * 트랜잭션 내에서 해당 상품의 기존 primary 이미지를 모두 false로 변경하고,
     * 대상 이미지를 primary로 설정합니다.
     * 
     * @param productId 상품 ID
     * @param imageUuid 설정할 이미지 UUID
     */
    @Transactional
    public void setPrimaryImage(Long productId, UUID imageUuid) {
        // 상품 존재 확인
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_PRODUCT_NOT_FOUND, productId));

        // 대상 이미지 조회
        ProductImage targetImage = productImageRepository.findByProductIdAndUuid(productId, imageUuid)
                .orElseThrow(() -> new IllegalArgumentException("이미지를 찾을 수 없습니다. productId=" + productId + ", imageUuid=" + imageUuid));

        // 해당 상품의 모든 primary 이미지를 false로 변경
        List<ProductImage> primaryImages = productImageRepository.findByProductIdAndPrimaryImage(productId, true);
        for (ProductImage image : primaryImages) {
            image.markAsSecondary();
            productImageRepository.save(image);
        }

        // 대상 이미지를 primary로 설정
        targetImage.markAsPrimary();
        productImageRepository.save(targetImage);

        log.info("Primary image set: productId={}, imageUuid={}", productId, imageUuid);
    }

    /**
     * 상품에 이미지를 추가합니다.
     * 첫 이미지면 primary=true로 설정하고, 아니면 false로 설정합니다.
     * 
     * @param productId 상품 ID
     * @param filePath 스토리지 키 (파일 경로)
     * @return 추가된 ProductImage
     */
    @Transactional
    public ProductImage addImage(Long productId, String filePath) {
        // 상품 존재 확인
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_PRODUCT_NOT_FOUND, productId));

        // 해당 상품의 기존 이미지 개수 확인
        List<ProductImage> existingImages = productImageRepository.findByProductId(productId);
        boolean isFirstImage = existingImages.isEmpty();

        // 이미지 생성
        ProductImage image = ProductImage.builder()
                .product(product)
                .filePath(filePath)
                .primaryImage(isFirstImage) // 첫 이미지면 primary=true
                .build();

        ProductImage saved = productImageRepository.save(image);
        log.info("Image added: productId={}, imageUuid={}, isPrimary={}", 
                productId, saved.getUuid(), saved.isPrimaryImage());

        return saved;
    }

    /**
     * 상품 이미지를 삭제합니다.
     * 삭제 대상이 primary였으면 남은 이미지 중 하나를 primary로 승격합니다.
     * 
     * @param productId 상품 ID
     * @param imageUuid 삭제할 이미지 UUID
     */
    @Transactional
    public void deleteImage(Long productId, UUID imageUuid) {
        // 상품 존재 확인
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_PRODUCT_NOT_FOUND, productId));

        // 삭제할 이미지 조회
        ProductImage imageToDelete = productImageRepository.findByProductIdAndUuid(productId, imageUuid)
                .orElseThrow(() -> new IllegalArgumentException("이미지를 찾을 수 없습니다. productId=" + productId + ", imageUuid=" + imageUuid));

        boolean wasPrimary = imageToDelete.isPrimaryImage();

        // 이미지 삭제
        productImageRepository.delete(imageToDelete);
        log.info("Image deleted: productId={}, imageUuid={}", productId, imageUuid);

        // 삭제된 이미지가 primary였고, 남은 이미지가 있으면 하나를 primary로 승격
        if (wasPrimary) {
            List<ProductImage> remainingImages = productImageRepository.findByProductId(productId);
            if (!remainingImages.isEmpty()) {
                // 정렬 기준: createdAt -> uuid
                ProductImage newPrimary = remainingImages.stream()
                        .sorted(Comparator.comparing(ProductImage::getCreatedAt)
                                .thenComparing(ProductImage::getUuid))
                        .findFirst()
                        .orElseThrow(); // 이미 isEmpty 체크했으므로 항상 존재

                newPrimary.markAsPrimary();
                productImageRepository.save(newPrimary);
                log.info("Primary image promoted: productId={}, imageUuid={}", 
                        productId, newPrimary.getUuid());
            }
        }
    }

    /**
     * 상품에 이미지를 연결합니다.
     * 첫 이미지면 primary=true로 설정하고, 아니면 false로 설정합니다.
     * 
     * @param productId 상품 ID
     * @param imageFilePaths 이미지 파일 경로 목록
     */
    @Transactional
    private void connectImagesToProduct(Long productId, List<String> imageFilePaths) {
        if (imageFilePaths == null || imageFilePaths.isEmpty()) {
            return;
        }

        // 상품 존재 확인
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_PRODUCT_NOT_FOUND, productId));

        // 기존 이미지 개수 확인 (첫 이미지 여부 판단)
        List<ProductImage> existingImages = productImageRepository.findByProductId(productId);
        boolean isFirstImage = existingImages.isEmpty();
        boolean firstImageSet = false; // 첫 번째 유효한 이미지 추적

        // 각 파일 경로에 대해 이미지 엔티티 생성
        for (String filePath : imageFilePaths) {
            if (filePath == null || filePath.trim().isEmpty()) {
                continue; // 빈 경로는 건너뜀
            }

            // 첫 번째 유효한 이미지면 primary=true, 나머지는 false
            boolean isPrimary = isFirstImage && !firstImageSet;
            if (isPrimary) {
                firstImageSet = true;
            }

            ProductImage image = ProductImage.builder()
                    .product(product)
                    .filePath(filePath.trim())
                    .primaryImage(isPrimary)
                    .build();

            productImageRepository.save(image);
            log.info("Image connected to product: productId={}, filePath={}, isPrimary={}", 
                    productId, filePath, isPrimary);
        }
    }

    /**
     * 상품의 이미지를 업데이트합니다.
     * imageFilePaths가 빈 리스트면 모든 이미지 제거, 값이 있으면 해당 이미지들로 교체합니다.
     * 
     * Product.images 컬렉션을 clear 후 새 이미지만 추가하여, orphanRemoval로 기존 삭제 + cascade로
     * 새 이미지 persist. repository 직접 delete/add 대신 컬렉션 기준으로 처리해 2중 반영(2배 복사)을 방지합니다.
     * 
     * @param product        상품 엔티티 (update 트랜잭션에서 로드된 것)
     * @param imageFilePaths 새로운 이미지 파일 경로 목록 (null이면 기존 유지, 빈 리스트면 모두 제거)
     */
    @Transactional
    private void updateProductImages(Product product, List<String> imageFilePaths) {
        long productId = product.getId();

        // 기존 이미지 제거: 컬렉션 clear → orphanRemoval로 DB 삭제
        product.getImages().clear();

        if (imageFilePaths == null || imageFilePaths.isEmpty()) {
            log.info("All images removed from product: productId={}", productId);
            return;
        }

        // 새 이미지 생성 후 product.images에만 추가 (cascade로 persist는 save(product) 시)
        boolean first = true;
        for (String filePath : imageFilePaths) {
            if (filePath == null || filePath.trim().isEmpty()) {
                continue;
            }
            ProductImage image = ProductImage.builder()
                    .product(product)
                    .filePath(filePath.trim())
                    .primaryImage(first)
                    .build();
            product.getImages().add(image);
            first = false;
            log.info("Image added to product: productId={}, filePath={}, isPrimary={}",
                    productId, filePath.trim(), image.isPrimaryImage());
        }
        log.info("Product images updated: productId={}, imageCount={}", productId, product.getImages().size());
    }

    /**
     * 상품에 Variants를 연결합니다.
     * 
     * @param product Product 엔티티
     * @param variantRequests Variant 요청 목록
     */
    @Transactional
    private void connectVariantsToProduct(Product product, List<ProductVariantRequest> variantRequests) {
        if (variantRequests == null || variantRequests.isEmpty()) {
            return;
        }

        for (ProductVariantRequest variantRequest : variantRequests) {
            ProductVariant variant = ProductVariant.builder()
                    .product(product)
                    .sku(variantRequest.getSku())
                    .optionText(variantRequest.getOptionText())
                    .price(variantRequest.getPrice())
                    .stockQty(variantRequest.getStockQty() != null ? variantRequest.getStockQty() : 0)
                    .active(variantRequest.getActive() != null ? variantRequest.getActive() : true)
                    .build();

            product.getVariants().add(variant);
            log.info("Variant connected to product: productId={}, sku={}", product.getId(), variant.getSku());
        }
    }

    /**
     * 상품의 Variants를 업데이트합니다.
     * variants가 빈 리스트면 모든 variants 제거, 값이 있으면 해당 variants로 교체합니다.
     * 
     * @param product Product 엔티티
     * @param variantRequests 새로운 Variant 요청 목록 (null이면 기존 유지, 빈 리스트면 모두 제거)
     */
    @Transactional
    private void updateProductVariants(Product product, List<ProductVariantRequest> variantRequests) {
        // 기존 variants 모두 제거
        product.getVariants().clear();

        // 빈 리스트면 모든 variants 제거하고 종료
        if (variantRequests.isEmpty()) {
            log.info("All variants removed from product: productId={}", product.getId());
            return;
        }

        // 새로운 variants로 교체
        connectVariantsToProduct(product, variantRequests);
        log.info("Product variants updated: productId={}, variantCount={}", product.getId(), variantRequests.size());
    }

    /**
     * categoryTypes(Enum 이름) 또는 categoryIds를 카테고리 ID 목록으로 반환합니다.
     * categoryTypes가 있으면 우선 사용하고, 해당 타입의 루트 Category ID로 변환합니다.
     * 
     * Lazy Create: Category가 없으면 자동으로 루트 Category를 생성합니다.
     * Enum 선택 → 저장 시 알아서 Category가 추가되는 방식입니다.
     *
     * @param categoryTypes Enum 이름 목록 (FOOD, SUPPLEMENT 등)
     * @param categoryIds   카테고리 ID 목록 (categoryTypes가 없을 때 사용)
     * @return ID 목록. 둘 다 없으면 null(기존 유지), 빈 리스트면 전체 제거 의미
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

    /**
     * CategoryType에 해당하는 루트 Category를 조회하거나, 없으면 생성합니다.
     * Lazy Create 패턴: Enum 선택 시 자동으로 Category가 생성됩니다.
     * 
     * 동시성 문제 방지:
     * - 이 메서드는 @Transactional 메서드(create/update) 내에서 호출되므로,
     *   같은 트랜잭션 내에서는 일관성 보장
     * - 다른 트랜잭션이 동시에 생성하려 하면, save 후 다시 조회하여 기존 것을 사용
     * 
     * 권장사항 (프로덕션 환경):
     * - DB에 (category_type, parent_id) unique constraint 추가 시 더 안전합니다.
     *   예: ALTER TABLE categories ADD UNIQUE KEY uk_category_type_parent (category_type, parent_id);
     * - 또는 분산 환경에서는 Redis Lock 등을 사용할 수 있습니다.
     *
     * @param categoryType CategoryType enum
     * @return 루트 Category (parent=null)
     */
    @Transactional
    private Category getOrCreateRootCategory(CategoryType categoryType) {
        // 1. 기존 루트 Category 조회
        return categoryRepository.findByCategoryTypeAndParentIsNull(categoryType)
                .orElseGet(() -> {
                    // 2. 없으면 생성
                    log.info("Creating root category for type: {} (Lazy Create)", categoryType);
                    Category newCategory = Category.builder()
                            .parent(null) // 루트 카테고리
                            .categoryType(categoryType)
                            .sortOrder(0)
                            .build();
                    
                    Category saved = categoryRepository.saveAndFlush(newCategory);
                    
                    // 3. saveAndFlush 후 다시 조회 (다른 트랜잭션이 먼저 생성했을 수 있음)
                    // 트랜잭션 격리 수준에 따라, 다른 트랜잭션의 커밋된 데이터를 볼 수 있음
                    return categoryRepository.findByCategoryTypeAndParentIsNull(categoryType)
                            .orElse(saved); // 혹시 모를 경우를 대비해 saved 반환
                });
    }

    /**
     * 상품에 Categories를 연결합니다.
     *
     * @param product Product 엔티티
     * @param categoryIds 카테고리 ID 목록
     */
    @Transactional
    private void connectCategoriesToProduct(Product product, List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }

        for (Long categoryId : categoryIds) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_CATEGORY_NOT_FOUND, categoryId));

            ProductCategory productCategory = ProductCategory.builder()
                    .product(product)
                    .category(category)
                    .build();

            productCategoryRepository.save(productCategory);
            log.info("Category connected to product: productId={}, categoryId={}", product.getId(), categoryId);
        }
    }

    /**
     * 상품의 Categories를 업데이트합니다.
     * categoryIds가 빈 리스트면 모든 categories 제거, 값이 있으면 해당 categories로 교체합니다.
     * 
     * @param product Product 엔티티
     * @param categoryIds 새로운 카테고리 ID 목록 (null이면 기존 유지, 빈 리스트면 모두 제거)
     */
    @Transactional
    private void updateProductCategories(Product product, List<Long> categoryIds) {
        // 기존 categories 모두 제거
        List<ProductCategory> existingCategories = productCategoryRepository.findById_ProductId(product.getId());
        for (ProductCategory productCategory : existingCategories) {
            productCategoryRepository.delete(productCategory);
        }

        // 빈 리스트면 모든 categories 제거하고 종료
        if (categoryIds.isEmpty()) {
            log.info("All categories removed from product: productId={}", product.getId());
            return;
        }

        // 새로운 categories로 교체
        connectCategoriesToProduct(product, categoryIds);
        log.info("Product categories updated: productId={}, categoryCount={}", product.getId(), categoryIds.size());
    }
}

package com.backend.service.shop;

import com.backend.common.dto.PageRequest;
import com.backend.common.dto.PageResponse;
import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.domain.member.Member;
import com.backend.domain.shop.Product;
import com.backend.domain.shop.ProductImage;
import com.backend.dto.shop.mapper.ProductMapper;
import com.backend.dto.shop.request.ProductCreateRequest;
import com.backend.dto.shop.request.ProductSearchRequest;
import com.backend.dto.shop.request.ProductUpdateRequest;
import com.backend.dto.shop.response.ProductImageResponse;
import com.backend.dto.shop.response.ProductResponse;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.shop.ProductImageRepository;
import com.backend.repository.shop.ProductRepository;
import com.backend.repository.shop.ProductSearch;
import com.backend.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductSearch productSearch;
    private final ProductMapper productMapper;
    private final MemberRepository memberRepository;
    private final FileStorageService fileStorageService;
    private final ProductImageRepository productImageRepository;

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
                .createdBy(member)
                .build();

        // 저장
        Product saved = productRepository.save(product);
        log.info("Product created: id={}, name={}", saved.getId(), saved.getName());

        // 이미지 연결 (imageFilePaths가 있으면)
        if (request.getImageFilePaths() != null && !request.getImageFilePaths().isEmpty()) {
            connectImagesToProduct(saved.getId(), request.getImageFilePaths());
        }

        // DTO 변환 및 이미지 URL 조립
        return toResponseWithImages(saved);
    }

    @Override
    public ProductResponse findById(Long id) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_PRODUCT_NOT_FOUND, id));

        return toResponseWithImages(product);
    }

    @Override
    public PageResponse<ProductResponse> findAll(PageRequest pageRequest, ProductSearchRequest searchRequest) {
        Page<Product> products = productSearch.search(
                searchRequest.toCondition(),
                pageRequest.toPageable()
        );

        return PageResponse.of(
                products.map(this::toResponseWithImages),
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

        // 이미지 업데이트 (imageFilePaths가 null이 아니면 처리)
        if (request.getImageFilePaths() != null) {
            updateProductImages(id, request.getImageFilePaths());
        }

        Product updated = productRepository.save(product);
        log.info("Product updated: id={}", updated.getId());

        return toResponseWithImages(updated);
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
     * Product 엔티티를 ProductResponse로 변환하고 이미지 URL을 조립합니다.
     * 
     * @param product Product 엔티티
     * @return 이미지 URL이 조립된 ProductResponse
     */
    private ProductResponse toResponseWithImages(Product product) {
        ProductResponse response = productMapper.toResponse(product);
        
        // 이미지 목록을 ProductImageResponse로 변환
        List<ProductImageResponse> imageResponses = product.getImages().stream()
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
     * ProductImage 엔티티를 ProductImageResponse로 변환하고 URL을 조립합니다.
     * 
     * @param image ProductImage 엔티티
     * @return URL이 조립된 ProductImageResponse
     */
    private ProductImageResponse toImageResponse(ProductImage image) {
        String url;
        String filePath = image.getFilePath();
        
        // 점진적 마이그레이션: filePath가 있으면 filePath 사용, 없으면 기존 url 사용
        if (filePath != null && !filePath.trim().isEmpty()) {
            url = fileStorageService.getFileUrl(filePath);
        } else if (image.getUrl() != null && !image.getUrl().trim().isEmpty()) {
            // 기존 데이터 호환성 유지
            url = image.getUrl();
        } else {
            url = null;
        }
        
        return ProductImageResponse.builder()
                .uuid(image.getUuid())
                .url(url)
                .filePath(filePath) // 프론트엔드에서 이미지 수정 시 필요
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
     * @param productId 상품 ID
     * @param imageFilePaths 새로운 이미지 파일 경로 목록 (null이면 기존 유지, 빈 리스트면 모두 제거)
     */
    @Transactional
    private void updateProductImages(Long productId, List<String> imageFilePaths) {
        // 상품 존재 확인
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_PRODUCT_NOT_FOUND, productId));

        // 기존 이미지 조회
        List<ProductImage> existingImages = productImageRepository.findByProductId(productId);

        // 빈 리스트면 모든 이미지 제거
        if (imageFilePaths.isEmpty()) {
            for (ProductImage image : existingImages) {
                productImageRepository.delete(image);
            }
            log.info("All images removed from product: productId={}", productId);
            return;
        }

        // 기존 이미지 모두 삭제
        for (ProductImage image : existingImages) {
            productImageRepository.delete(image);
        }

        // 새로운 이미지들로 교체
        connectImagesToProduct(productId, imageFilePaths);
        log.info("Product images updated: productId={}, imageCount={}", productId, imageFilePaths.size());
    }
}

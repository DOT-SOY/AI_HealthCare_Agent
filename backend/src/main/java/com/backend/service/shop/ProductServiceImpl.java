package com.backend.service.shop;

import com.backend.common.dto.PageRequest;
import com.backend.common.dto.PageResponse;
import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.domain.member.Member;
import com.backend.domain.shop.Product;
import com.backend.dto.shop.mapper.ProductMapper;
import com.backend.dto.shop.request.ProductCreateRequest;
import com.backend.dto.shop.request.ProductSearchRequest;
import com.backend.dto.shop.request.ProductUpdateRequest;
import com.backend.dto.shop.response.ProductResponse;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.shop.ProductRepository;
import com.backend.repository.shop.ProductSearch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductSearch productSearch;
    private final ProductMapper productMapper;
    private final MemberRepository memberRepository;

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

        // DTO 변환
        return productMapper.toResponse(saved);
    }

    @Override
    public ProductResponse findById(Long id) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_PRODUCT_NOT_FOUND, id));

        return productMapper.toResponse(product);
    }

    @Override
    public PageResponse<ProductResponse> findAll(PageRequest pageRequest, ProductSearchRequest searchRequest) {
        Page<Product> products = productSearch.search(
                searchRequest.toCondition(),
                pageRequest.toPageable()
        );

        return PageResponse.of(
                products.map(productMapper::toResponse),
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

        Product updated = productRepository.save(product);
        log.info("Product updated: id={}", updated.getId());

        return productMapper.toResponse(updated);
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
}

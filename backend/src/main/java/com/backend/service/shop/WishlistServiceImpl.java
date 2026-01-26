package com.backend.service.shop;

import com.backend.common.dto.PageRequest;
import com.backend.common.dto.PageResponse;
import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.domain.member.Member;
import com.backend.domain.shop.Product;
import com.backend.domain.shop.ProductImage;
import com.backend.domain.shop.Wishlist;
import com.backend.dto.shop.response.WishlistItemResponse;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.shop.ProductRepository;
import com.backend.repository.shop.WishlistRepository;
import com.backend.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepository;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    public boolean toggleWishlist(Long memberId, Long productId) {
        // Member 존재 확인
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, memberId));

        // Product 존재 확인
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_PRODUCT_NOT_FOUND, productId));

        // 기존 찜 확인
        boolean exists = wishlistRepository.existsById_MemberIdAndId_ProductId(memberId, productId);

        if (exists) {
            // 찜 제거
            wishlistRepository.deleteById_MemberIdAndId_ProductId(memberId, productId);
            log.info("Wishlist removed: memberId={}, productId={}", memberId, productId);
            return false;
        } else {
            // 찜 추가
            Wishlist wishlist = Wishlist.builder()
                    .member(member)
                    .product(product)
                    .build();
            wishlistRepository.save(wishlist);
            log.info("Wishlist added: memberId={}, productId={}", memberId, productId);
            return true;
        }
    }
    
    @Override
    public PageResponse<WishlistItemResponse> findAll(Long memberId, PageRequest pageRequest) {
        // Member 존재 확인
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, memberId));
        
        // 찜 목록 페이징 조회
        Page<Wishlist> wishlistPage = wishlistRepository.findAllById_MemberId(
                memberId, 
                pageRequest.toPageable()
        );
        
        // WishlistItemResponse로 변환
        var items = wishlistPage.getContent().stream()
                .map(wishlist -> toWishlistItemResponse(wishlist.getProduct()))
                .collect(Collectors.toList());
        
        return PageResponse.of(
                org.springframework.data.support.PageableExecutionUtils.getPage(
                        items,
                        wishlistPage.getPageable(),
                        wishlistPage::getTotalElements
                ),
                pageRequest.getPage()
        );
    }
    
    /**
     * Product를 WishlistItemResponse로 변환
     */
    private WishlistItemResponse toWishlistItemResponse(Product product) {
        // Primary 이미지 찾기
        String primaryImageUrl = null;
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            primaryImageUrl = product.getImages().stream()
                    .filter(ProductImage::isPrimaryImage)
                    .min(Comparator.comparing(ProductImage::getCreatedAt))
                    .map(image -> {
                        String filePath = image.getFilePath();
                        return (filePath != null && !filePath.trim().isEmpty())
                                ? fileStorageService.getFileUrl(filePath)
                                : null;
                    })
                    .orElse(null);
        }
        
        return WishlistItemResponse.builder()
                .productId(product.getId())
                .productName(product.getName())
                .productDescription(product.getDescription())
                .primaryImageUrl(primaryImageUrl)
                .createdAt(product.getCreatedAt())
                .build();
    }
}

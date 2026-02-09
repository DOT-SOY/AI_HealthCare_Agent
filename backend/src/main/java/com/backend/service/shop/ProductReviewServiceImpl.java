package com.backend.service.shop;

import com.backend.common.dto.PageRequest;
import com.backend.common.dto.PageResponse;
import com.backend.domain.member.Member;
import com.backend.domain.order.OrderItemStatus;
import com.backend.domain.order.OrderStatus;
import com.backend.domain.shop.Product;
import com.backend.domain.shop.ProductReview;
import com.backend.domain.shop.ProductReviewImage;
import com.backend.domain.shop.ProductReviewReply;
import com.backend.dto.shop.request.ReviewCreateRequest;
import com.backend.dto.shop.request.ReviewUpdateRequest;
import com.backend.dto.shop.response.ReplyResponse;
import com.backend.dto.shop.response.ReviewImageResponse;
import com.backend.dto.shop.response.ReviewResponse;
import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.repository.order.OrderItemRepository;
import com.backend.repository.shop.ProductReviewImageRepository;
import com.backend.repository.shop.ProductReviewReplyRepository;
import com.backend.repository.shop.ProductReviewRepository;
import com.backend.repository.shop.ProductRepository;
import com.backend.service.file.FileStorageService;
import com.backend.service.member.CurrentMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
public class ProductReviewServiceImpl implements ProductReviewService {

    private static final List<OrderStatus> PAID_OR_LATER = List.of(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final ProductReviewRepository productReviewRepository;
    private final ProductReviewReplyRepository productReviewReplyRepository;
    private final ProductReviewImageRepository productReviewImageRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final CurrentMemberService currentMemberService;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    public ReviewResponse create(Long productId, ReviewCreateRequest request, Long memberId) {
        Member member = currentMemberService.getCurrentMemberOrThrow();
        if (!member.getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.SHOP_REVIEW_FORBIDDEN);
        }
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_PRODUCT_NOT_FOUND, productId));
        if (!orderItemRepository.existsByMemberIdAndProductIdAndOrderStatusInAndItemStatus(
                memberId, productId, PAID_OR_LATER, OrderItemStatus.ORDERED)) {
            throw new BusinessException(ErrorCode.SHOP_REVIEW_NOT_ELIGIBLE);
        }
        if (productReviewRepository.existsByProductIdAndMemberId(productId, memberId)) {
            throw new BusinessException(ErrorCode.SHOP_REVIEW_ALREADY_EXISTS);
        }
        ProductReview review = ProductReview.builder()
                .product(product)
                .member(member)
                .rating(request.getRating())
                .content(request.getContent())
                .build();
        ProductReview saved = productReviewRepository.save(review);

        // 이미지가 있다면 저장
        List<ProductReviewImage> images = List.of();
        if (request.getImageFilePaths() != null && !request.getImageFilePaths().isEmpty()) {
            List<String> validPaths = filterValidPaths(request.getImageFilePaths());
            images = validPaths.stream()
                    .map(path -> ProductReviewImage.builder()
                            .review(saved)
                            .filePath(path)
                            .build())
                    .toList();
            productReviewImageRepository.saveAll(images);
        }

        return toReviewResponse(saved, List.of(), images);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> findByProductId(Long productId, PageRequest pageRequest) {
        Page<ProductReview> page = productReviewRepository.findByProductIdOrderByCreatedAtDesc(
                productId, pageRequest.toPageable());
        List<ProductReview> content = page.getContent();
        if (content.isEmpty()) {
            return PageResponse.<ReviewResponse>builder()
                    .items(List.of())
                    .page(pageRequest.getPage())
                    .pageSize(pageRequest.getPageSize())
                    .total(page.getTotalElements())
                    .pages(page.getTotalPages())
                    .hasNext(page.hasNext())
                    .hasPrevious(page.hasPrevious())
                    .build();
        }
        List<Long> reviewIds = content.stream().map(ProductReview::getId).toList();
        List<ProductReviewReply> allReplies = productReviewReplyRepository.findByReviewIdInOrderByCreatedAtAsc(reviewIds);
        Map<Long, List<ReplyResponse>> repliesByReviewId = allReplies.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getReview().getId(),
                        Collectors.mapping(this::toReplyResponse, Collectors.toList())
                ));
        List<ProductReviewImage> allImages = productReviewImageRepository.findByReviewIdIn(reviewIds);
        Map<Long, List<ProductReviewImage>> imagesByReviewId = allImages.stream()
                .collect(Collectors.groupingBy(img -> img.getReview().getId()));

        List<ReviewResponse> items = content.stream()
                .map(r -> toReviewResponse(
                        r,
                        repliesByReviewId.getOrDefault(r.getId(), List.of()),
                        imagesByReviewId.getOrDefault(r.getId(), List.of())
                ))
                .toList();
        return PageResponse.<ReviewResponse>builder()
                .items(items)
                .page(pageRequest.getPage())
                .pageSize(pageRequest.getPageSize())
                .total(page.getTotalElements())
                .pages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

    @Override
    @Transactional
    public ReviewResponse update(Long reviewId, ReviewUpdateRequest request, Long memberId) {
        Member member = currentMemberService.getCurrentMemberOrThrow();
        if (!member.getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.SHOP_REVIEW_FORBIDDEN);
        }
        ProductReview review = productReviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_REVIEW_NOT_FOUND, reviewId));
        if (!review.getMember().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.SHOP_REVIEW_FORBIDDEN);
        }
        if (request.getRating() != null) {
            review.update(request.getRating(), request.getContent());
        } else if (request.getContent() != null) {
            review.update(review.getRating(), request.getContent());
        }

        // 이미지가 null이 아닌 경우에만 교체 처리 (null이면 기존 이미지 유지)
        // 빈 배열도 명시적으로 처리하여 모든 이미지 제거 가능
        List<ProductReviewImage> finalImages;
        if (request.getImageFilePaths() != null) {
            List<ProductReviewImage> existingImages = productReviewImageRepository.findByReviewId(reviewId);
            
            // 요청된 filePath 목록 (정규화)
            List<String> requestedPaths = filterValidPaths(request.getImageFilePaths());
            
            // 기존 이미지의 filePath 집합
            Set<String> existingPaths = existingImages.stream()
                    .map(ProductReviewImage::getFilePath)
                    .collect(Collectors.toSet());
            
            // 요청된 filePath 집합
            Set<String> requestedPathsSet = new HashSet<>(requestedPaths);
            
            // 삭제할 이미지: 기존에 있지만 요청에 없는 것
            List<ProductReviewImage> imagesToDelete = existingImages.stream()
                    .filter(img -> !requestedPathsSet.contains(img.getFilePath()))
                    .toList();
            
            // 추가할 이미지: 요청에 있지만 기존에 없는 것
            List<String> pathsToAdd = requestedPaths.stream()
                    .filter(path -> !existingPaths.contains(path))
                    .toList();
            
            // 삭제 처리 (DB + 실제 파일)
            if (!imagesToDelete.isEmpty()) {
                List<String> filePathsToDelete = extractFilePaths(imagesToDelete);
                
                // DB 레코드 물리적 삭제
                productReviewImageRepository.deleteAll(imagesToDelete);
                
                // 실제 파일도 삭제
                deleteImageFiles(filePathsToDelete);
            }
            
            // 추가 처리: 새로 추가할 이미지만 저장
            if (!pathsToAdd.isEmpty()) {
                List<ProductReviewImage> newImages = pathsToAdd.stream()
                        .map(path -> ProductReviewImage.builder()
                                .review(review)
                                .filePath(path)
                                .build())
                        .toList();
                productReviewImageRepository.saveAll(newImages);
            }
            
            // 최종 이미지 목록 구성: 유지할 이미지 + 새로 추가한 이미지
            Set<String> requestedPathsSetForFilter = new HashSet<>(requestedPaths);
            List<ProductReviewImage> keptImages = existingImages.stream()
                    .filter(img -> requestedPathsSetForFilter.contains(img.getFilePath()))
                    .toList();
            
            finalImages = new ArrayList<>(keptImages);
            if (!pathsToAdd.isEmpty()) {
                // 새로 추가한 이미지 조회
                List<ProductReviewImage> addedImages = productReviewImageRepository.findByReviewId(reviewId).stream()
                        .filter(img -> pathsToAdd.contains(img.getFilePath()))
                        .toList();
                finalImages.addAll(addedImages);
            }
        } else {
            // 이미지 변경 없음 - 기존 이미지 조회
            finalImages = productReviewImageRepository.findByReviewId(reviewId);
        }

        List<ProductReviewReply> replies = productReviewReplyRepository.findByReviewIdInOrderByCreatedAtAsc(List.of(reviewId));
        return toReviewResponse(
                review,
                replies.stream().map(this::toReplyResponse).toList(),
                finalImages
        );
    }

    @Override
    @Transactional
    public void delete(Long reviewId, Long memberId) {
        Member member = currentMemberService.getCurrentMemberOrThrow();
        if (!member.getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.SHOP_REVIEW_FORBIDDEN);
        }
        ProductReview review = productReviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_REVIEW_NOT_FOUND, reviewId));
        if (!review.getMember().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.SHOP_REVIEW_FORBIDDEN);
        }
        // 리뷰 이미지를 먼저 물리적 삭제 (외래키 제약조건 해결)
        List<ProductReviewImage> images = productReviewImageRepository.findByReviewId(reviewId);
        List<String> filePathsToDelete = extractFilePaths(images);
        
        // DB 레코드 물리적 삭제 (엔티티 기반 삭제로 영속성 컨텍스트 문제 방지)
        if (!images.isEmpty()) {
            productReviewImageRepository.deleteAll(images);
        }
        
        // 실제 파일도 삭제
        deleteImageFiles(filePathsToDelete);
        
        // 그 다음 리뷰 삭제
        productReviewRepository.delete(review);
    }

    private ReviewResponse toReviewResponse(ProductReview review, List<ReplyResponse> replies, List<ProductReviewImage> images) {
        List<ReviewImageResponse> imageResponses = images.stream()
                .map(this::toReviewImageResponse)
                .toList();

        return ReviewResponse.builder()
                .id(review.getId())
                .productId(review.getProduct().getId())
                .memberId(review.getMember().getId())
                .displayName(maskDisplayName(review.getMember().getName()))
                .rating(review.getRating())
                .content(review.getContent())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .replies(replies)
                .images(imageResponses)
                .build();
    }

    private ReplyResponse toReplyResponse(ProductReviewReply reply) {
        return ReplyResponse.builder()
                .id(reply.getId())
                .reviewId(reply.getReview().getId())
                .content(reply.getContent())
                .authorDisplayName(reply.getMember().getName())
                .createdAt(reply.getCreatedAt())
                .build();
    }

    private ReviewImageResponse toReviewImageResponse(ProductReviewImage image) {
        String filePath = image.getFilePath();
        String url = isValidFilePath(filePath)
                ? fileStorageService.getFileUrl(filePath)
                : null;

        return ReviewImageResponse.builder()
                .uuid(image.getUuid())
                .filePath(filePath)
                .url(url)
                .build();
    }

    private String maskDisplayName(String name) {
        if (name == null || name.length() < 2) {
            return name != null ? name : "";
        }
        return name.charAt(0) + "*".repeat(Math.max(0, name.length() - 2)) + name.charAt(name.length() - 1);
    }

    /**
     * filePath가 유효한지 검증합니다.
     */
    private boolean isValidFilePath(String path) {
        return path != null && !path.trim().isEmpty();
    }

    /**
     * filePath 목록에서 유효한 경로만 필터링하고 정규화합니다.
     */
    private List<String> filterValidPaths(List<String> paths) {
        return paths.stream()
                .filter(this::isValidFilePath)
                .map(String::trim)
                .toList();
    }

    /**
     * 이미지 엔티티 목록에서 filePath를 추출합니다.
     */
    private List<String> extractFilePaths(List<ProductReviewImage> images) {
        return images.stream()
                .map(ProductReviewImage::getFilePath)
                .filter(this::isValidFilePath)
                .toList();
    }

    /**
     * 이미지 파일들을 삭제합니다. 실패해도 로그만 남기고 계속 진행합니다.
     */
    private void deleteImageFiles(List<String> filePaths) {
        for (String filePath : filePaths) {
            try {
                fileStorageService.delete(filePath);
            } catch (Exception e) {
                log.info("Failed to delete review image file: {}", filePath, e);
            }
        }
    }
}

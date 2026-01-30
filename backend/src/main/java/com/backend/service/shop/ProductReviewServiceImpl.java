package com.backend.service.shop;

import com.backend.common.dto.PageRequest;
import com.backend.common.dto.PageResponse;
import com.backend.domain.member.Member;
import com.backend.domain.order.OrderItemStatus;
import com.backend.domain.order.OrderStatus;
import com.backend.domain.shop.Product;
import com.backend.domain.shop.ProductReview;
import com.backend.domain.shop.ProductReviewReply;
import com.backend.dto.shop.request.ReviewCreateRequest;
import com.backend.dto.shop.request.ReviewUpdateRequest;
import com.backend.dto.shop.response.ReplyResponse;
import com.backend.dto.shop.response.ReviewResponse;
import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.repository.order.OrderItemRepository;
import com.backend.repository.shop.ProductReviewReplyRepository;
import com.backend.repository.shop.ProductReviewRepository;
import com.backend.repository.shop.ProductRepository;
import com.backend.service.member.CurrentMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductReviewServiceImpl implements ProductReviewService {

    private static final List<OrderStatus> PAID_OR_LATER = List.of(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final ProductReviewRepository productReviewRepository;
    private final ProductReviewReplyRepository productReviewReplyRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final CurrentMemberService currentMemberService;

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
        return toReviewResponse(saved, List.of());
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
        List<ReviewResponse> items = content.stream()
                .map(r -> toReviewResponse(r, repliesByReviewId.getOrDefault(r.getId(), List.of())))
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
        productReviewRepository.flush();
        List<ProductReviewReply> replies = productReviewReplyRepository.findByReviewIdInOrderByCreatedAtAsc(List.of(reviewId));
        return toReviewResponse(review, replies.stream().map(this::toReplyResponse).toList());
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
        productReviewRepository.delete(review);
    }

    private ReviewResponse toReviewResponse(ProductReview review, List<ReplyResponse> replies) {
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

    private String maskDisplayName(String name) {
        if (name == null || name.length() < 2) {
            return name != null ? name : "";
        }
        return name.charAt(0) + "*".repeat(Math.max(0, name.length() - 2)) + name.charAt(name.length() - 1);
    }
}

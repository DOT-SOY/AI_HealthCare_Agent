package com.backend.service.shop;

import com.backend.dto.shop.request.ReplyCreateRequest;
import com.backend.dto.shop.response.ReplyResponse;
import com.backend.domain.member.Member;
import com.backend.domain.member.MemberRole;
import com.backend.domain.shop.ProductReview;
import com.backend.domain.shop.ProductReviewReply;
import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.repository.shop.ProductReviewReplyRepository;
import com.backend.repository.shop.ProductReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewReplyService {

    private final ProductReviewRepository productReviewRepository;
    private final ProductReviewReplyRepository productReviewReplyRepository;

    @Transactional
    public ReplyResponse create(Long reviewId, ReplyCreateRequest request, Member currentMember) {
        if (!isAdmin(currentMember)) {
            throw new BusinessException(ErrorCode.SHOP_REVIEW_REPLY_FORBIDDEN);
        }
        ProductReview review = productReviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_REVIEW_NOT_FOUND, reviewId));
        ProductReviewReply reply = ProductReviewReply.builder()
                .review(review)
                .member(currentMember)
                .content(request.getContent())
                .build();
        ProductReviewReply saved = productReviewReplyRepository.save(reply);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long reviewId, Long replyId, Member currentMember) {
        if (!isAdmin(currentMember)) {
            throw new BusinessException(ErrorCode.SHOP_REVIEW_REPLY_FORBIDDEN);
        }
        ProductReviewReply reply = productReviewReplyRepository.findById(replyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_REVIEW_REPLY_NOT_FOUND, replyId));
        if (!reply.getReview().getId().equals(reviewId)) {
            throw new BusinessException(ErrorCode.SHOP_REVIEW_REPLY_NOT_FOUND, replyId);
        }
        productReviewReplyRepository.delete(reply);
    }

    public List<ReplyResponse> findByReviewIds(List<Long> reviewIds) {
        if (reviewIds == null || reviewIds.isEmpty()) {
            return List.of();
        }
        return productReviewReplyRepository.findByReviewIdInOrderByCreatedAtAsc(reviewIds).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private ReplyResponse toResponse(ProductReviewReply reply) {
        return ReplyResponse.builder()
                .id(reply.getId())
                .reviewId(reply.getReview().getId())
                .content(reply.getContent())
                .authorDisplayName(reply.getMember().getName())
                .createdAt(reply.getCreatedAt())
                .build();
    }

    private boolean isAdmin(Member member) {
        return member.getRoleList() != null && member.getRoleList().contains(MemberRole.ADMIN);
    }
}

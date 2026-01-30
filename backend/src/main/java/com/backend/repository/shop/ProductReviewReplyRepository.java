package com.backend.repository.shop;

import com.backend.domain.shop.ProductReviewReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductReviewReplyRepository extends JpaRepository<ProductReviewReply, Long> {

    List<ProductReviewReply> findByReviewIdInOrderByCreatedAtAsc(List<Long> reviewIds);
}

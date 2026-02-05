package com.backend.repository.shop;

import com.backend.domain.shop.ProductReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProductReviewImageRepository extends JpaRepository<ProductReviewImage, UUID> {

    List<ProductReviewImage> findByReviewIdIn(List<Long> reviewIds);

    List<ProductReviewImage> findByReviewId(Long reviewId);

    // 리뷰 ID로 이미지를 물리적으로 삭제
    @Modifying
    @Query("DELETE FROM ProductReviewImage p WHERE p.review.id = :reviewId")
    void deleteAllByReviewId(@Param("reviewId") Long reviewId);
}

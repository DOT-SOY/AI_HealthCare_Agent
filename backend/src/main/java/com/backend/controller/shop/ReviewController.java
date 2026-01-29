package com.backend.controller.shop;

import com.backend.dto.shop.request.ReviewUpdateRequest;
import com.backend.dto.shop.response.ReviewResponse;
import com.backend.service.member.CurrentMemberService;
import com.backend.service.shop.ProductReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ProductReviewService productReviewService;
    private final CurrentMemberService currentMemberService;

    @PatchMapping("/{id}")
    public ResponseEntity<ReviewResponse> update(
            @PathVariable("id") Long reviewId,
            @Valid @RequestBody ReviewUpdateRequest request) {
        var member = currentMemberService.getCurrentMemberOrThrow();
        ReviewResponse response = productReviewService.update(reviewId, request, member.getId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable("id") Long reviewId) {
        var member = currentMemberService.getCurrentMemberOrThrow();
        productReviewService.delete(reviewId, member.getId());
        return ResponseEntity.noContent().build();
    }
}

package com.backend.controller.shop;

import com.backend.common.dto.PageRequest;
import com.backend.common.dto.PageResponse;
import com.backend.dto.shop.request.ReviewCreateRequest;
import com.backend.dto.shop.response.ReviewResponse;
import com.backend.service.member.CurrentMemberService;
import com.backend.service.shop.ProductReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products/{productId}/reviews")
@RequiredArgsConstructor
public class ProductReviewController {

    private final ProductReviewService productReviewService;
    private final CurrentMemberService currentMemberService;

    @GetMapping
    public ResponseEntity<PageResponse<ReviewResponse>> findByProductId(
            @PathVariable Long productId,
            @Valid @ModelAttribute PageRequest pageRequest) {
        PageResponse<ReviewResponse> response = productReviewService.findByProductId(productId, pageRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ReviewResponse> create(
            @PathVariable Long productId,
            @Valid @RequestBody ReviewCreateRequest request) {
        var member = currentMemberService.getCurrentMemberOrThrow();
        ReviewResponse response = productReviewService.create(productId, request, member.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

package com.backend.controller.shop;

import com.backend.dto.shop.request.ReplyCreateRequest;
import com.backend.dto.shop.response.ReplyResponse;
import com.backend.service.member.CurrentMemberService;
import com.backend.service.shop.ReviewReplyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews/{reviewId}/replies")
@RequiredArgsConstructor
public class ReviewReplyController {

    private final ReviewReplyService reviewReplyService;
    private final CurrentMemberService currentMemberService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReplyResponse> create(
            @PathVariable Long reviewId,
            @Valid @RequestBody ReplyCreateRequest request) {
        var member = currentMemberService.getCurrentMemberOrThrow();
        ReplyResponse response = reviewReplyService.create(reviewId, request, member);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{replyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable Long reviewId,
            @PathVariable Long replyId) {
        var member = currentMemberService.getCurrentMemberOrThrow();
        reviewReplyService.delete(reviewId, replyId, member);
        return ResponseEntity.noContent().build();
    }
}

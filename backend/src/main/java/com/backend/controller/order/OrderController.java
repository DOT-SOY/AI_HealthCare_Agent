package com.backend.controller.order;

import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.dto.order.request.OrderGuestLookupRequest;
import com.backend.dto.order.response.OrderDetailResponse;
import com.backend.repository.member.MemberRepository;
import com.backend.dto.payment.response.PaymentReadyResponse;
import com.backend.service.payment.PaymentService;
import com.backend.service.order.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final MemberRepository memberRepository;
    private final PaymentService paymentService;

    /**
     * 회원 주문 상세 조회
     * - 로그인한 member의 주문만 접근 가능
     */
    @GetMapping("/{orderNo}")
    public ResponseEntity<OrderDetailResponse> getOrderDetail(@PathVariable String orderNo) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof String)) {
            throw new BusinessException(ErrorCode.JWT_ERROR);
        }

        String email = (String) authentication.getPrincipal();
        var member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, email));

        if (member.isDeleted()) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND, email);
        }

        OrderDetailResponse response = orderService.getOrderDetailForMember(orderNo, member.getId());
        return ResponseEntity.ok(response);
    }

    /**
     * 결제 준비 (Toss 결제 위젯용)
     * - 주문 상태가 CREATED 인 경우에만 허용
     * - 로그인한 회원의 주문만 접근 가능
     */
    @PostMapping("/{orderNo}/pay/ready")
    public ResponseEntity<PaymentReadyResponse> preparePayment(@PathVariable String orderNo) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof String)) {
            throw new BusinessException(ErrorCode.JWT_ERROR);
        }

        String email = (String) authentication.getPrincipal();
        var member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, email));

        if (member.isDeleted()) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND, email);
        }

        PaymentReadyResponse response = paymentService.prepareTossPayment(orderNo, member.getId());
        return ResponseEntity.ok(response);
    }

    /**
     * 비회원 주문 조회
     */
    @PostMapping("/guest-lookup")
    public ResponseEntity<OrderDetailResponse> guestLookup(
            @Valid @RequestBody OrderGuestLookupRequest request) {

        OrderDetailResponse response = orderService.getOrderDetailForGuest(
                request.getOrderNo(),
                request.getGuestPhone(),
                request.getGuestPassword()
        );

        return ResponseEntity.ok(response);
    }
}


package com.backend.controller.order;

import com.backend.dto.order.request.OrderCreateFromCartRequest;
import com.backend.dto.order.request.OrderGuestLookupRequest;
import com.backend.dto.order.response.OrderCreateFromCartResponse;
import com.backend.dto.order.response.OrderDetailResponse;
import com.backend.dto.payment.response.PaymentReadyResponse;
import com.backend.service.payment.PaymentService;
import com.backend.service.order.OrderService;
import com.backend.service.member.CurrentMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final CurrentMemberService currentMemberService;
    private final PaymentService paymentService;

    /**
     * 장바구니 기준 주문 생성 (from-cart)
     * - 로그인 회원만 사용. 장바구니 조회 → 검증 → Order + OrderItem + 스냅샷 생성, OrderStatus = CREATED
     */
    @PostMapping("/from-cart")
    public ResponseEntity<OrderCreateFromCartResponse> createOrderFromCart(
            @Valid @RequestBody OrderCreateFromCartRequest request) {
        var member = currentMemberService.getCurrentMemberOrThrow();
        OrderCreateFromCartResponse response = orderService.createOrderFromCart(member.getId(), request);
        return ResponseEntity.ok(response);
    }

    /**
     * 회원 주문 상세 조회
     * - 로그인한 member의 주문만 접근 가능
     */
    @GetMapping("/{orderNo}")
    public ResponseEntity<OrderDetailResponse> getOrderDetail(@PathVariable String orderNo) {
        var member = currentMemberService.getCurrentMemberOrThrow();
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
        var member = currentMemberService.getCurrentMemberOrThrow();
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


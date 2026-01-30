package com.backend.controller.order;

import com.backend.common.dto.PageResponse;
import com.backend.dto.order.request.OrderCreateFromCartRequest;
import com.backend.dto.order.request.OrderGuestLookupRequest;
import com.backend.dto.order.request.OrderListRequest;
import com.backend.dto.order.response.OrderCreateFromCartResponse;
import com.backend.dto.order.response.OrderDetailResponse;
import com.backend.dto.order.response.OrderSummaryResponse;
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
     * 회원 본인 주문 목록 조회 (페이지네이션, 기간·상태 필터). JWT 인증 필수.
     */
    @GetMapping("/me")
    public ResponseEntity<PageResponse<OrderSummaryResponse>> getMyOrders(
            @Valid @ModelAttribute OrderListRequest request) {
        var member = currentMemberService.getCurrentMemberOrThrow();
        PageResponse<OrderSummaryResponse> response = orderService.getMyOrders(member.getId(), request);
        return ResponseEntity.ok(response);
    }

    /**
     * 회원 주문 상세 조회
     * - 로그인한 member의 주문만 접근 가능
     */
    @GetMapping("/{orderNo}")
    public ResponseEntity<OrderDetailResponse> getOrderDetail(@PathVariable("orderNo") String orderNo) {
        var member = currentMemberService.getCurrentMemberOrThrow();
        OrderDetailResponse response = orderService.getOrderDetailForMember(orderNo, member.getId());
        return ResponseEntity.ok(response);
    }

    /**
     * 회원 주문 배송지 스냅샷 수정
     * - 로그인한 member의 주문만 접근 가능
     * - 주문 상태가 SHIPPED/DELIVERED/CANCELED 인 경우 수정 불가
     */
    @PatchMapping("/{orderNo}/ship-to")
    public ResponseEntity<Void> updateShipTo(
            @PathVariable("orderNo") String orderNo,
            @Valid @RequestBody OrderCreateFromCartRequest.ShipToDto request
    ) {
        var member = currentMemberService.getCurrentMemberOrThrow();
        orderService.updateShipToForMember(orderNo, member.getId(), request);
        return ResponseEntity.noContent().build();
    }

    /**
     * 결제 준비 (Toss 결제 위젯용)
     * - 주문 상태가 CREATED 인 경우에만 허용
     * - 로그인한 회원의 주문만 접근 가능
     */
    @PostMapping("/{orderNo}/pay/ready")
    public ResponseEntity<PaymentReadyResponse> preparePayment(@PathVariable("orderNo") String orderNo) {
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


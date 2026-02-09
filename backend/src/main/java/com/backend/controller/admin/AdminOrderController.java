package com.backend.controller.admin;

import com.backend.common.dto.PageResponse;
import com.backend.domain.order.OrderStatus;
import com.backend.dto.order.request.OrderListRequest;
import com.backend.dto.order.response.OrderDetailResponse;
import com.backend.dto.order.response.OrderSummaryResponse;
import com.backend.service.order.OrderService;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자 주문 목록 조회 및 배송 상태 변경 API
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderService orderService;

    /**
     * 관리자용 전체 주문 목록 (페이지네이션, 기간·상태 필터)
     */
    @GetMapping
    public ResponseEntity<PageResponse<OrderSummaryResponse>> getAdminOrders(
            @Valid @ModelAttribute OrderListRequest request) {
        PageResponse<OrderSummaryResponse> response = orderService.getAdminOrders(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 관리자용 주문 상세 조회 (전체 유저 주문)
     */
    @GetMapping("/{orderNo}")
    public ResponseEntity<OrderDetailResponse> getOrderDetail(
            @PathVariable("orderNo") String orderNo) {
        OrderDetailResponse response = orderService.getOrderDetailForAdmin(orderNo);
        return ResponseEntity.ok(response);
    }

    /**
     * 관리자용 주문 배송 상태 변경 (SHIPPED, DELIVERED, CANCELED)
     */
    @PatchMapping("/{orderNo}/status")
    public ResponseEntity<Void> updateOrderStatus(
            @PathVariable("orderNo") String orderNo,
            @Valid @RequestBody OrderStatusUpdateRequest body) {
        orderService.updateOrderStatusForAdmin(orderNo, body.getStatus());
        return ResponseEntity.noContent().build();
    }

    @Getter
    @Setter
    public static class OrderStatusUpdateRequest {
        private OrderStatus status;
    }
}

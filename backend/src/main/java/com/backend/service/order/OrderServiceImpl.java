package com.backend.service.order;

import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.domain.order.Order;
import com.backend.dto.order.response.OrderDetailResponse;
import com.backend.repository.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public OrderDetailResponse getOrderDetailForMember(String orderNo, Long memberId) {
        Order order = orderRepository.findDetailByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_ORDER_NOT_FOUND, orderNo));

        if (order.getMember() == null || !order.getMember().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.SHOP_ORDER_ACCESS_DENIED, orderNo);
        }

        return OrderDetailResponse.from(order);
    }

    @Override
    public OrderDetailResponse getOrderDetailForGuest(String orderNo, String guestPhone, String guestPassword) {
        Order order = orderRepository.findDetailByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_ORDER_NOT_FOUND, orderNo));

        // 회원 주문이면 비회원 조회 불가
        if (order.getMember() != null) {
            throw new BusinessException(ErrorCode.SHOP_ORDER_ACCESS_DENIED, orderNo);
        }

        if (order.getGuestPhone() == null || order.getGuestPasswordHash() == null) {
            throw new BusinessException(ErrorCode.SHOP_ORDER_GUEST_AUTH_FAILED);
        }

        if (!order.getGuestPhone().equals(guestPhone)
                || !passwordEncoder.matches(guestPassword, order.getGuestPasswordHash())) {
            throw new BusinessException(ErrorCode.SHOP_ORDER_GUEST_AUTH_FAILED);
        }

        return OrderDetailResponse.from(order);
    }
}


package com.backend.service.order;

import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.domain.cart.Cart;
import com.backend.domain.cart.CartItem;
import com.backend.domain.member.Member;
import com.backend.domain.order.Order;
import com.backend.domain.order.OrderBuyerSnapshot;
import com.backend.domain.order.OrderItem;
import com.backend.domain.order.OrderItemStatus;
import com.backend.domain.order.OrderShipToSnapshot;
import com.backend.domain.order.OrderStatus;
import com.backend.domain.shop.ProductStatus;
import com.backend.domain.shop.ProductVariant;
import com.backend.common.dto.PageResponse;
import com.backend.dto.order.request.OrderCreateFromCartRequest;
import com.backend.dto.order.request.OrderListRequest;
import com.backend.dto.order.response.OrderCreateFromCartResponse;
import com.backend.dto.order.response.OrderDetailResponse;
import com.backend.dto.order.response.OrderSummaryResponse;
import com.backend.repository.cart.CartRepository;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.order.OrderItemSummaryProjection;
import com.backend.repository.order.OrderRepository;
import com.backend.repository.order.OrderSummaryBaseProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public OrderCreateFromCartResponse createOrderFromCart(Long memberId, OrderCreateFromCartRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, memberId));

        Cart cart = cartRepository.findWithItemsByMember_Id(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_CART_EMPTY));

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.SHOP_CART_EMPTY);
        }

        BigDecimal totalItemAmount = BigDecimal.ZERO;
        for (CartItem cartItem : cart.getItems()) {
            ProductVariant variant = cartItem.getVariant();
            var product = variant.getProduct();
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.SHOP_PRODUCT_INVALID_STATUS);
            }
            if (!variant.isActive()) {
                throw new BusinessException(ErrorCode.SHOP_VARIANT_INACTIVE, variant.getId());
            }
            if (variant.getStockQty() < cartItem.getQty()) {
                throw new BusinessException(ErrorCode.SHOP_VARIANT_OUT_OF_STOCK, cartItem.getQty(), variant.getStockQty());
            }
            BigDecimal unitPrice = variant.resolvePrice();
            totalItemAmount = totalItemAmount.add(unitPrice.multiply(BigDecimal.valueOf(cartItem.getQty())));
        }

        BigDecimal shippingFee = BigDecimal.ZERO;
        BigDecimal totalPayableAmount = totalItemAmount.add(shippingFee);
        String orderNo = "ORD-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        Order order = Order.builder()
                .orderNo(orderNo)
                .member(member)
                .status(OrderStatus.CREATED)
                .totalPayableAmount(totalPayableAmount)
                .shippingFee(shippingFee)
                .build();

        for (CartItem cartItem : cart.getItems()) {
            ProductVariant variant = cartItem.getVariant();
            var product = variant.getProduct();
            BigDecimal unitPrice = variant.resolvePrice();
            BigDecimal lineAmount = unitPrice.multiply(BigDecimal.valueOf(cartItem.getQty()));
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .variant(variant)
                    .status(OrderItemStatus.ORDERED)
                    .productNameSnapshot(product.getName())
                    .variantSnapshot(variant.getOptionText())
                    .unitPriceSnapshot(unitPrice)
                    .qty(cartItem.getQty())
                    .lineAmount(lineAmount)
                    .build();
            order.addItem(orderItem);
        }

        OrderBuyerSnapshot buyerSnapshot = OrderBuyerSnapshot.builder()
                .order(order)
                .buyerName(request.getBuyer().getBuyerName())
                .buyerEmail(request.getBuyer().getBuyerEmail())
                .buyerPhone(request.getBuyer().getBuyerPhone())
                .build();
        order.setBuyerSnapshot(buyerSnapshot);

        OrderShipToSnapshot shipToSnapshot = OrderShipToSnapshot.builder()
                .order(order)
                .recipientName(request.getShipTo().getRecipientName())
                .recipientPhone(request.getShipTo().getRecipientPhone())
                .zipcode(request.getShipTo().getZipcode())
                .address1(request.getShipTo().getAddress1())
                .address2(request.getShipTo().getAddress2())
                .build();
        order.setShipToSnapshot(shipToSnapshot);

        order = orderRepository.save(order);

        return OrderCreateFromCartResponse.builder()
                .orderNo(order.getOrderNo())
                .amount(order.getTotalPayableAmount())
                .orderName("주문 " + order.getOrderNo())
                .orderId(order.getId())
                .build();
    }

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
    public PageResponse<OrderSummaryResponse> getMyOrders(Long memberId, OrderListRequest request) {
        ZoneId zone = ZoneId.systemDefault();
        Instant fromDateStart = request.getFromDate() == null
                ? null
                : request.getFromDate().atStartOfDay(zone).toInstant();
        Instant toDateEnd = request.getToDate() == null
                ? null
                : request.getToDate().plusDays(1).atStartOfDay(zone).toInstant();

        Page<OrderSummaryBaseProjection> page = orderRepository.findSummaryByMemberId(
                memberId,
                fromDateStart,
                toDateEnd,
                request.getStatus(),
                request.toPageable());

        List<OrderSummaryBaseProjection> content = page.getContent();
        if (content.isEmpty()) {
            return PageResponse.<OrderSummaryResponse>builder()
                    .items(List.of())
                    .page(request.getPage())
                    .pageSize(page.getSize())
                    .total(page.getTotalElements())
                    .pages(page.getTotalPages())
                    .hasNext(page.hasNext())
                    .hasPrevious(page.hasPrevious())
                    .build();
        }

        List<Long> orderIds = content.stream().map(OrderSummaryBaseProjection::getId).toList();
        List<OrderItemSummaryProjection> itemSummaries = orderRepository.findOrderItemSummaryByOrderIds(orderIds);
        Map<Long, OrderItemSummaryProjection> itemSummaryByOrderId = itemSummaries.stream()
                .collect(Collectors.toMap(OrderItemSummaryProjection::getOrderId, s -> s));

        List<OrderSummaryResponse> items = content.stream()
                .map(base -> OrderSummaryResponse.from(base, itemSummaryByOrderId.get(base.getId())))
                .toList();

        return PageResponse.<OrderSummaryResponse>builder()
                .items(items)
                .page(request.getPage())
                .pageSize(page.getSize())
                .total(page.getTotalElements())
                .pages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
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

    @Override
    @Transactional
    public void updateShipToForMember(String orderNo, Long memberId, OrderCreateFromCartRequest.ShipToDto shipToDto) {
        Order order = orderRepository.findWithDetailsByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_ORDER_NOT_FOUND, orderNo));

        if (order.getMember() == null || !order.getMember().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.SHOP_ORDER_ACCESS_DENIED, orderNo);
        }

        if (order.isShippedOrLater()) {
            throw new BusinessException(ErrorCode.SHOP_ORDER_SHIPTO_UPDATE_NOT_ALLOWED, orderNo);
        }

        OrderShipToSnapshot shipTo = order.getShipToSnapshot();
        shipTo.update(
                shipToDto.getRecipientName(),
                shipToDto.getRecipientPhone(),
                shipToDto.getZipcode(),
                shipToDto.getAddress1(),
                shipToDto.getAddress2()
        );
    }
}


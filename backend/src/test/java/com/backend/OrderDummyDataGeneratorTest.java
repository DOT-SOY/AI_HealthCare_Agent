package com.backend;

import com.backend.domain.member.Member;
import com.backend.domain.order.*;
import com.backend.domain.shop.Product;
import com.backend.domain.shop.ProductStatus;
import com.backend.domain.shop.ProductVariant;
import com.backend.repository.member.MemberRepository;
import com.backend.repository.order.OrderRepository;
import com.backend.repository.shop.ProductRepository;
import com.backend.repository.shop.ProductVariantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 주문 더미데이터 생성 테스트.
 * 회원 3명 이상만 DB에 있으면 됨. 상품/옵션은 테스트 내에서 더미 1개씩 생성하며,
 * 주문 상품 정보(스냅샷)는 임의 값으로 채움.
 *
 * 사용 방법:
 * 1. IDE에서 이 테스트를 실행
 * 2. 또는 Gradle: ./gradlew test --tests "com.backend.OrderDummyDataGeneratorTest.generateOrderDummyData"
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("주문 더미데이터 생성")
class OrderDummyDataGeneratorTest {

    private static final String[] RANDOM_WORDS = {
            "알파", "브라보", "찰리", "델타", "에코", "폭스", "골프", "호텔",
            "인디고", "줄리엣", "킬로", "리마", "마이크", "노벰버", "오스카", "파파"
    };

    private static final String[] PRODUCT_NAME_SNAPSHOTS = {
            "프로틴 파우더", "비타민C", "오메가3", "크레아틴", "BCAA",
            "멀티비타민", "콜라겐", "유산균", "비오틴", "철분"
    };

    private static final String[] VARIANT_SNAPSHOTS = {
            "용량: 500g", "용량: 1kg", "맛: 초코", "맛: 바닐라", "30일분",
            "60일분", "90일분", "옵션: 기본", "옵션: 프리미엄"
    };

    private static final OrderStatus[] TARGET_STATUSES = {
            OrderStatus.PAID, OrderStatus.PAID, OrderStatus.PAID, OrderStatus.PAID,
            OrderStatus.SHIPPED, OrderStatus.SHIPPED, OrderStatus.SHIPPED,
            OrderStatus.DELIVERED, OrderStatus.DELIVERED, OrderStatus.DELIVERED
    };

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Test
    @Transactional
    @Rollback(false)
    @DisplayName("주문 10개 + order_items·스냅샷 생성 (결제완료/배송중/배송완료)")
    void generateOrderDummyData() throws Exception {
        List<Member> members = memberRepository.findAll(
                PageRequest.of(0, 3, Sort.by("id"))).getContent();
        if (members.size() < 3) {
            throw new IllegalStateException(
                    "주문 더미데이터 생성에는 최소 3명의 회원이 필요합니다. 현재: " + members.size() + "명");
        }

        Member productOwner = members.get(0);
        Product product = Product.builder()
                .name("주문더미상품")
                .description("주문 더미데이터용 상품")
                .status(ProductStatus.ACTIVE)
                .basePrice(new BigDecimal("10000"))
                .createdBy(productOwner)
                .build();
        Product savedProduct = productRepository.save(product);

        ProductVariant variant = ProductVariant.builder()
                .product(savedProduct)
                .optionText("기본옵션")
                .price(new BigDecimal("10000"))
                .stockQty(1000)
                .active(true)
                .build();
        ProductVariant savedVariant = productVariantRepository.save(variant);

        BigDecimal shippingFee = new BigDecimal("3000");
        String suffix = "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        for (int i = 0; i < 10; i++) {
            Member member = members.get(i % 3);
            String word1 = RANDOM_WORDS[i % RANDOM_WORDS.length];
            String word2 = RANDOM_WORDS[(i + 5) % RANDOM_WORDS.length];
            String orderNo = "ORD-DUMMY-" + word1 + "-" + word2 + suffix + "-" + i;

            String productNameSnap = PRODUCT_NAME_SNAPSHOTS[i % PRODUCT_NAME_SNAPSHOTS.length];
            String variantSnap = VARIANT_SNAPSHOTS[i % VARIANT_SNAPSHOTS.length];
            BigDecimal unitPrice = new BigDecimal(5000 + (i * 1000));
            int qty = 1 + (i % 3);
            BigDecimal lineAmount = unitPrice.multiply(BigDecimal.valueOf(qty));
            BigDecimal totalPayableAmount = lineAmount.add(shippingFee);

            Order order = Order.builder()
                    .orderNo(orderNo)
                    .member(member)
                    .status(OrderStatus.CREATED)
                    .totalPayableAmount(totalPayableAmount)
                    .shippingFee(shippingFee)
                    .build();

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(Objects.requireNonNull(savedProduct))
                    .variant(Objects.requireNonNull(savedVariant))
                    .status(OrderItemStatus.ORDERED)
                    .productNameSnapshot(productNameSnap)
                    .variantSnapshot(variantSnap)
                    .unitPriceSnapshot(unitPrice)
                    .qty(qty)
                    .lineAmount(lineAmount)
                    .build();
            order.addItem(item);

            OrderBuyerSnapshot buyerSnapshot = OrderBuyerSnapshot.builder()
                    .order(order)
                    .buyerName(member.getName())
                    .buyerEmail(member.getEmail())
                    .buyerPhone("010-0000-" + String.format("%04d", member.getId()))
                    .build();
            order.setBuyerSnapshot(buyerSnapshot);

            OrderShipToSnapshot shipToSnapshot = OrderShipToSnapshot.builder()
                    .order(order)
                    .recipientName(member.getName() + " (수령인)")
                    .recipientPhone("010-1111-" + String.format("%04d", i))
                    .zipcode("12345")
                    .address1("서울시 강남구 테스트로 " + (i + 1) + "번지")
                    .address2("동 " + (i + 1) + "호")
                    .build();
            order.setShipToSnapshot(shipToSnapshot);

            order = orderRepository.save(order);

            Instant paidAt = Instant.now().minusSeconds(86400L * (10 - i));
            order.markPaid(paidAt);
            order.markFinalized();

            OrderStatus targetStatus = TARGET_STATUSES[i];
            if (targetStatus == OrderStatus.SHIPPED || targetStatus == OrderStatus.DELIVERED) {
                setOrderStatus(order, targetStatus);
                orderRepository.save(order);
            }
        }

        System.out.println("=== 주문 더미데이터 생성 완료: 10건 (결제완료/배송중/배송완료) ===");
    }

    private static void setOrderStatus(Order order, OrderStatus status) throws Exception {
        Field statusField = Order.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(order, status);
    }
}

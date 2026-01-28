package com.backend.service.payment;

import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.common.exception.PaymentAlreadyProcessedException;
import com.backend.domain.order.Order;
import com.backend.domain.order.OrderStatus;
import com.backend.domain.payment.Payment;
import com.backend.domain.payment.PaymentProvider;
import com.backend.domain.payment.PaymentStatus;
import com.backend.dto.payment.request.TossPaymentConfirmRequest;
import com.backend.dto.payment.response.PaymentReadyResponse;
import com.backend.dto.payment.response.TossPaymentConfirmResponse;
import com.backend.domain.order.OrderItem;
import com.backend.repository.order.OrderRepository;
import com.backend.repository.payment.PaymentRepository;
import com.backend.repository.shop.ProductVariantRepository;
import com.backend.service.cart.CartKey;
import com.backend.service.cart.CartService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private static final String TOSS_CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CartService cartService;
    private final ObjectMapper objectMapper;

    @Value("${toss.payments.client-key:}")
    private String tossClientKey;

    @Value("${toss.payments.secret-key:}")
    private String tossSecretKey;

    /**
     * PaymentStatus 우선순위 (역행 방지용).
     * APPROVED > READY > FAILED 순으로 간주한다.
     */
    private int compareStatusPriority(PaymentStatus a, PaymentStatus b) {
        return Integer.compare(getPriority(a), getPriority(b));
    }

    private int getPriority(PaymentStatus status) {
        if (status == PaymentStatus.APPROVED) {
            return 3;
        }
        if (status == PaymentStatus.READY) {
            return 2;
        }
        if (status == PaymentStatus.FAILED) {
            return 1;
        }
        return 0;
    }

    @Override
    @Transactional
    public PaymentReadyResponse prepareTossPayment(String orderNo, Long memberId) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_ORDER_NOT_FOUND, orderNo));

        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BusinessException(ErrorCode.SHOP_PAYMENT_INVALID_ORDER_STATE, orderNo, order.getStatus());
        }

        if (order.getMember() == null || !order.getMember().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.SHOP_ORDER_ACCESS_DENIED, orderNo);
        }

        order.toPaymentPending();

        String readyPaymentKey = "ready:" + orderNo;
        upsertPaymentByKey(order, readyPaymentKey, PaymentStatus.READY, null, null);

        String orderName = "주문 " + orderNo;
        // 토스 customerKey: 영문 대소문자, 숫자, -, _, =, ., @ 로 2~50자. 회원은 long(memberId)이므로 "m_" 접두사로 규격 맞춤.
        String customerKey = memberId != null ? ("m_" + memberId) : null;
        return PaymentReadyResponse.builder()
                .orderId(orderNo)
                .amount(order.getTotalPayableAmount())
                .orderName(orderName)
                .clientKey(tossClientKey != null ? tossClientKey : "")
                .customerKey(customerKey)
                .build();
    }

    @Override
    @Transactional
    public TossPaymentConfirmResponse confirmTossPayment(TossPaymentConfirmRequest request) {
        String paymentKey = request.getPaymentKey();
        String orderNo = request.getOrderId();
        BigDecimal expectedAmount = BigDecimal.valueOf(request.getAmount());

        // 1) paymentKey 기준 멱등: 이미 APPROVED면 그대로 성공 응답 (finalize 재실행 금지)
        var existingByKey = paymentRepository.findByPaymentKey(paymentKey);
        if (existingByKey.isPresent() && existingByKey.get().getStatus() == PaymentStatus.APPROVED) {
            Payment p = existingByKey.get();
            return TossPaymentConfirmResponse.of(
                    p.getOrder().getOrderNo(), OrderStatus.PAID, p.getOrder().getTotalPayableAmount(), p.getApprovedAt());
        }

        Order order = orderRepository.findDetailByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_ORDER_NOT_FOUND, orderNo));

        if (order.getTotalPayableAmount().compareTo(expectedAmount) != 0) {
            throw new BusinessException(ErrorCode.SHOP_PAYMENT_AMOUNT_MISMATCH,
                    expectedAmount, order.getTotalPayableAmount());
        }

        // 이미 PAID면 멱등 성공 (order 기준, finalize 재실행 금지)
        if (order.getStatus() == OrderStatus.PAID) {
            Instant at = order.getPaidAt() != null ? order.getPaidAt() : Instant.now();
            return TossPaymentConfirmResponse.of(orderNo, OrderStatus.PAID, order.getTotalPayableAmount(), at);
        }

        if (order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            throw new BusinessException(ErrorCode.SHOP_PAYMENT_INVALID_ORDER_STATE, orderNo, order.getStatus());
        }

        Payment readyPayment = paymentRepository.findByOrder_OrderNoAndStatus(orderNo, PaymentStatus.READY).orElse(null);

        // 2) Toss 승인 API 호출 (실패 시 READY Payment를 FAILED로)
        String rawResponse;
        try {
            rawResponse = callTossConfirmApi(request);
        } catch (PaymentAlreadyProcessedException e) {
            // 토스 "이미 처리된 결제" → 멱등: 이미 해당 paymentKey로 APPROVED가 있으면 그대로 성공 (동시 요청 등)
            // Toss "이미 처리된 결제" → 멱등: 이미 해당 paymentKey로 APPROVED가 있으면 그대로 성공
            var existingPayment = paymentRepository.findByPaymentKey(request.getPaymentKey());
            if (existingPayment.isPresent() && existingPayment.get().getStatus() == PaymentStatus.APPROVED) {
                Payment p = existingPayment.get();
                return TossPaymentConfirmResponse.of(
                        p.getOrder().getOrderNo(), OrderStatus.PAID, p.getOrder().getTotalPayableAmount(), p.getApprovedAt());
            }
            // 주문이 이미 PAID면 그대로 반환 (finalize 재실행 금지)
            if (order.getStatus() == OrderStatus.PAID) {
                Instant at = order.getPaidAt() != null ? order.getPaidAt() : Instant.now();
                return TossPaymentConfirmResponse.of(orderNo, OrderStatus.PAID, order.getTotalPayableAmount(), at);
            }
            // 그 외 케이스에서는 Toss 측이 이미 결제를 승인했으나 로컬 상태가 뒤쳐진 것으로 보고,
            // 결제 결과만 반영(applyPaymentResult) 후 finalize는 수행하지 않는다.
            Instant approvedAt = Instant.now();
            applyPaymentResult(order, request.getPaymentKey(), "{\"alreadyProcessed\":true}", approvedAt);
            return TossPaymentConfirmResponse.of(orderNo, OrderStatus.PAID, order.getTotalPayableAmount(), approvedAt);
        } catch (BusinessException e) {
            if (readyPayment != null) {
                readyPayment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(readyPayment);
            }
            throw e;
        }
        Instant approvedAt = Instant.now();

        // 3) 결제 결과 반영 (OrderStatus/PaymentStatus) - 1단계
        applyPaymentResult(order, paymentKey, rawResponse, approvedAt);

        // 4) 재고 및 카트 정리 - 2단계 (confirm 경로에서만 수행)
        // 여기서 CartItem 등 동시성/낙관락 이슈가 나더라도,
        // 결제/주문 자체는 이미 PAID 로 반영된 상태이므로 승인 트랜잭션까지 롤백시키지 않는다.
        try {
            finalizeAfterPaid(order);
        } catch (ObjectOptimisticLockingFailureException |
                 jakarta.persistence.OptimisticLockException |
                 org.hibernate.StaleObjectStateException e) {
            log.warn("Optimistic lock during finalizeAfterPaid. " +
                            "Order is already marked PAID, skipping cart cleanup. orderNo={}, msg={}",
                    orderNo, e.getMessage(), e);
        }

        return TossPaymentConfirmResponse.of(orderNo, order.getStatus(), order.getTotalPayableAmount(), approvedAt);
    }

    /**
     * 1단계: 결제 결과 반영.
     * - Order: CREATED/PAYMENT_PENDING → PAID 전이
     * - Payment: paymentKey 기준 upsert + APPROVED 상태 반영
     * - 멱등: 이미 PAID/APPROVED면 상태를 역행시키지 않음.
     */
    @Transactional
    protected void applyPaymentResult(Order order, String paymentKey, String rawResponse, Instant approvedAt) {
        if (order.getStatus() == OrderStatus.PAID) {
            // 이미 결제 완료된 주문이면 더 이상 상태를 변경하지 않는다.
            return;
        }

        order.markPaid(approvedAt);
        upsertPaymentByKey(order, paymentKey, PaymentStatus.APPROVED, approvedAt, rawResponse);
    }

    /**
     * 2단계: 결제 완료 이후 후처리.
     * - 재고 락 + 차감
     * - 장바구니 정리
     * 멱등 요구사항에 따라, 이미 PAID/APPROVED인 경우라도 이 메서드를 재호출하지 않도록
     * 호출부에서 제어한다(Confirm 경로에서만 1회 호출).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void finalizeAfterPaid(Order order) {
        // 재고 재검증 + 차감 (락)
        for (OrderItem oi : order.getItems()) {
            var variant = productVariantRepository.findByIdForUpdate(oi.getVariant().getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_VARIANT_NOT_FOUND, oi.getVariant().getId()));
            if (!variant.isActive()) {
                throw new BusinessException(ErrorCode.SHOP_VARIANT_INACTIVE, variant.getId());
            }
            if (variant.getStockQty() < oi.getQty()) {
                throw new BusinessException(ErrorCode.SHOP_VARIANT_OUT_OF_STOCK, oi.getQty(), variant.getStockQty());
            }
            variant.decreaseStock(oi.getQty());
        }

        // 서버에서 장바구니 비우기
        if (order.getMember() != null) {
            cartService.clearCart(CartKey.ofMember(order.getMember().getId()));
        }
    }

    /**
     * paymentKey 기준 Payment upsert.
     * - 이미 존재하는 경우 상태 역행(예: APPROVED → READY) 방지
     * - APPROVED > READY > FAILED 우선순위를 사용.
     */
    @Transactional
    protected Payment upsertPaymentByKey(Order order,
                                         String paymentKey,
                                         PaymentStatus targetStatus,
                                         Instant approvedAt,
                                         String rawResponse) {
        try {
            return paymentRepository.findByPaymentKey(paymentKey)
                    .map(existing -> {
                        // 상태 역행 방지: 더 낮은 우선순위로 덮어쓰지 않는다.
                        if (compareStatusPriority(targetStatus, existing.getStatus()) < 0) {
                            return existing;
                        }
                        existing.setStatus(targetStatus);
                        if (approvedAt != null) {
                            existing.setApprovedAt(approvedAt);
                        }
                        if (rawResponse != null) {
                            existing.setRawResponse(rawResponse);
                        }
                        return paymentRepository.save(existing);
                    })
                    .orElseGet(() -> {
                        Payment payment = Payment.builder()
                                .order(order)
                                .provider(PaymentProvider.TOSS)
                                .status(targetStatus)
                                .paymentKey(paymentKey)
                                .approvedAt(approvedAt)
                                .rawResponse(rawResponse)
                                .build();
                        return paymentRepository.save(payment);
                    });
        } catch (DataIntegrityViolationException ex) {
            // 동시성으로 인해 payment_key 중복이 발생한 경우, 다시 조회해서 가장 최신 상태를 사용한다.
            log.warn("Payment upsert duplicate key: paymentKey={}", paymentKey, ex);
            return paymentRepository.findByPaymentKey(paymentKey)
                    .orElseThrow(() -> new PaymentAlreadyProcessedException(order.getOrderNo()));
        }
    }

    private String callTossConfirmApi(TossPaymentConfirmRequest request) {
        if (tossSecretKey == null || tossSecretKey.isBlank()) {
            throw new BusinessException(ErrorCode.SHOP_PAYMENT_CONFIG_NOT_FOUND);
        }

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String basicAuthValue = Base64.getEncoder()
                .encodeToString((tossSecretKey + ":").getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + basicAuthValue);

        Map<String, Object> body = new HashMap<>();
        body.put("paymentKey", request.getPaymentKey());
        body.put("orderId", request.getOrderId());
        body.put("amount", request.getAmount());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(TOSS_CONFIRM_URL, HttpMethod.POST, entity, String.class);
        } catch (RestClientException e) {
            log.error("Toss confirm API call failed: {}", e.getMessage(), e);
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (e instanceof HttpStatusCodeException hex) {
                String responseBody = hex.getResponseBodyAsString();
                if (responseBody != null) msg = msg + " " + responseBody;
            }
            if (msg.contains("ALREADY_PROCESSED_PAYMENT") || msg.contains("이미 처리된 결제")) {
                throw new PaymentAlreadyProcessedException(request.getOrderId());
            }
            if (msg.contains("S008") || msg.contains("기존 요청을 처리중") || msg.contains("FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING")) {
                throw new BusinessException(ErrorCode.SHOP_PAYMENT_PROCESSING_RETRY);
            }
            throw new BusinessException(ErrorCode.SHOP_PAYMENT_CONFIRM_FAILED, e.getMessage());
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Toss confirm API response not successful. status={}, body={}",
                    response.getStatusCode(), response.getBody());
            throw new BusinessException(ErrorCode.SHOP_PAYMENT_CONFIRM_FAILED, response.getStatusCode());
        }

        return response.getBody();
    }

    @Override
    @Transactional
    public void handleTossWebhook(String payload, String transmissionTime, String signature) {
        // 1) (선택) 시그니처 검증 - 실패해도 일단 로그만 남기고 처리 계속 (문서 기준 TODO)
        verifySignatureSoft(payload, transmissionTime, signature);

        // 2) 페이로드 파싱
        TossPaymentWebhookEvent event;
        try {
            event = objectMapper.readValue(payload, TossPaymentWebhookEvent.class);
        } catch (Exception e) {
            log.warn("Failed to parse Toss webhook payload: {}", e.getMessage());
            return; // 200 OK - 재전송 방지, 서버 로그만 남김
        }

        if (event.getData() == null) {
            log.warn("Toss webhook data is null. eventType={}", event.getEventType());
            return;
        }

        String eventType = event.getEventType();
        String orderId = event.getData().getOrderId();
        String paymentKey = event.getData().getPaymentKey();
        String paymentStatus = event.getData().getStatus();

        log.info("Received Toss webhook: type={}, orderId={}, paymentKey={}, status={}",
                eventType, orderId, paymentKey, paymentStatus);

        if (orderId == null || paymentKey == null) {
            log.warn("Toss webhook missing orderId or paymentKey. payload={}", payload);
            return;
        }

        // 현재는 PAYMENT_STATUS_CHANGED 만 처리 (필요 시 확장)
        if (!"PAYMENT_STATUS_CHANGED".equalsIgnoreCase(eventType)) {
            log.info("Ignoring Toss webhook eventType={}", eventType);
            return;
        }

        Order order = orderRepository.findDetailByOrderNo(orderId).orElse(null);
        if (order == null) {
            log.warn("Order not found for Toss webhook. orderId={}", orderId);
            return;
        }

        // 상태 동기화: DONE 상태를 결제 완료로 간주
        if ("DONE".equalsIgnoreCase(paymentStatus)) {
            // 이미 결제 완료면 멱등 처리
            if (order.getStatus() == OrderStatus.PAID) {
                log.info("Order already PAID. Ignoring duplicated webhook. orderId={}", orderId);
                return;
            }

            // CREATED 또는 PAYMENT_PENDING 상태일 때만 결제 결과를 반영 (정합성 보정).
            // 재고/카트는 confirm 경로가 원칙이므로 여기서는 applyPaymentResult까지만 수행한다.
            if (order.getStatus() == OrderStatus.CREATED || order.getStatus() == OrderStatus.PAYMENT_PENDING) {
                Instant approvedAt = Instant.now();
                applyPaymentResult(order, paymentKey, payload, approvedAt);
                log.info("Order/payment updated from Toss webhook (apply only). orderId={}, paymentKey={}", orderId, paymentKey);
            } else {
                log.info("Order status is not CREATED or PAYMENT_PENDING. Skipping webhook state change. orderId={}, status={}",
                        orderId, order.getStatus());
            }
        } else {
            // 그 외 상태는 일단 로그만 (취소/실패 동기화는 필요 시 확장)
            log.info("Unhandled Toss payment status from webhook. orderId={}, status={}", orderId, paymentStatus);
        }
    }

    private void verifySignatureSoft(String payload, String transmissionTime, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank() ||
                transmissionTime == null || transmissionTime.isBlank() ||
                tossSecretKey == null || tossSecretKey.isBlank()) {
            // 필수 정보 없으면 검증은 생략하고 로그만
            log.debug("Skipping Toss webhook signature verification (missing header or secret).");
            return;
        }

        try {
            String signingPayload = payload + ":" + transmissionTime;

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(tossSecretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(signingPayload.getBytes(StandardCharsets.UTF_8));

            // 헤더 형식: v1=BASE64_SIGNATURE,... (문서 기준, 포맷 변경 시 TODO)
            String[] parts = signatureHeader.split(",");
            boolean matched = false;
            for (String part : parts) {
                String trimmed = part.trim();
                int idx = trimmed.indexOf("v1=");
                if (idx < 0) continue;
                String b64 = trimmed.substring(idx + 3).trim();
                byte[] actual = Base64.getDecoder().decode(b64);
                if (constantTimeEquals(expected, actual)) {
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                log.warn("Toss webhook signature verification failed.");
            } else {
                log.debug("Toss webhook signature verification succeeded.");
            }
        } catch (Exception e) {
            log.warn("Toss webhook signature verification error: {}", e.getMessage());
        }
    }

    private boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TossPaymentWebhookEvent {
        private String eventType;

        @JsonProperty("data")
        private TossPaymentWebhookData data;

        public String getEventType() {
            return eventType;
        }

        public TossPaymentWebhookData getData() {
            return data;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TossPaymentWebhookData {
        private String orderId;
        private String paymentKey;
        private String status;

        public String getOrderId() {
            return orderId;
        }

        public String getPaymentKey() {
            return paymentKey;
        }

        public String getStatus() {
            return status;
        }
    }
}


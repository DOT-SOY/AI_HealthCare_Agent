package com.backend.service.payment;

import com.backend.common.exception.BusinessException;
import com.backend.common.exception.ErrorCode;
import com.backend.domain.order.Order;
import com.backend.domain.order.OrderStatus;
import com.backend.domain.payment.Payment;
import com.backend.domain.payment.PaymentProvider;
import com.backend.domain.payment.PaymentStatus;
import com.backend.dto.payment.request.TossPaymentConfirmRequest;
import com.backend.dto.payment.response.PaymentReadyResponse;
import com.backend.dto.payment.response.TossPaymentConfirmResponse;
import com.backend.repository.order.OrderRepository;
import com.backend.repository.payment.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final ObjectMapper objectMapper;

    @Value("${toss.payments.client-key:}")
    private String tossClientKey;

    @Value("${toss.payments.secret-key:}")
    private String tossSecretKey;

    @Override
    public PaymentReadyResponse prepareTossPayment(String orderNo, Long memberId) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_ORDER_NOT_FOUND, orderNo));

        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BusinessException(ErrorCode.SHOP_PAYMENT_INVALID_ORDER_STATE, orderNo, order.getStatus());
        }

        if (order.getMember() == null || !order.getMember().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.SHOP_ORDER_ACCESS_DENIED, orderNo);
        }

        String orderName = "주문 " + orderNo;

        return PaymentReadyResponse.builder()
                .orderId(orderNo)
                .amount(order.getTotalPayableAmount())
                .orderName(orderName)
                .build();
    }

    @Override
    @Transactional
    public TossPaymentConfirmResponse confirmTossPayment(TossPaymentConfirmRequest request) {
        String orderNo = request.getOrderId();

        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_ORDER_NOT_FOUND, orderNo));

        BigDecimal expectedAmount = order.getTotalPayableAmount();
        BigDecimal requestedAmount = BigDecimal.valueOf(request.getAmount());

        if (expectedAmount.compareTo(requestedAmount) != 0) {
            throw new BusinessException(ErrorCode.SHOP_PAYMENT_AMOUNT_MISMATCH,
                    requestedAmount, expectedAmount);
        }

        // 이미 결제 완료인 경우: 멱등 처리 - 기존 승인 시각으로 성공 응답
        if (order.getStatus() == OrderStatus.PAID) {
            Instant approvedAt = order.getPaidAt();
            if (approvedAt == null) {
                approvedAt = Instant.now();
            }
            return TossPaymentConfirmResponse.of(orderNo, order.getStatus(), expectedAmount, approvedAt);
        }

        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BusinessException(ErrorCode.SHOP_PAYMENT_INVALID_ORDER_STATE, orderNo, order.getStatus());
        }

        // Toss 결제 승인 API 호출
        String rawResponse = callTossConfirmApi(request);
        Instant approvedAt = Instant.now();

        // 주문 상태 갱신
        order.markPaid(approvedAt);

        // 결제 레코드 저장
        Payment payment = Payment.builder()
                .order(order)
                .provider(PaymentProvider.TOSS)
                .status(PaymentStatus.APPROVED)
                .paymentKey(request.getPaymentKey())
                .approvedAt(approvedAt)
                .rawResponse(rawResponse)
                .build();

        paymentRepository.save(payment);

        return TossPaymentConfirmResponse.of(orderNo, order.getStatus(), expectedAmount, approvedAt);
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

        Order order = orderRepository.findByOrderNo(orderId).orElse(null);
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

            // CREATED 상태일 때만 PAID로 변경 (그 외 상태는 로그만)
            if (order.getStatus() == OrderStatus.CREATED) {
                Instant approvedAt = Instant.now();
                order.markPaid(approvedAt);

                // 결제 레코드가 없으면 생성 (있으면 상태만 보정)
                Payment payment = paymentRepository.findByPaymentKey(paymentKey)
                        .orElseGet(() -> Payment.builder()
                                .order(order)
                                .provider(PaymentProvider.TOSS)
                                .paymentKey(paymentKey)
                                .build());

                payment = Payment.builder()
                        .order(order)
                        .provider(PaymentProvider.TOSS)
                        .paymentKey(paymentKey)
                        .status(PaymentStatus.APPROVED)
                        .approvedAt(approvedAt)
                        .rawResponse(payload)
                        .build();

                paymentRepository.save(payment);
                log.info("Order/payment updated from Toss webhook. orderId={}, paymentKey={}", orderId, paymentKey);
            } else {
                log.info("Order status is not CREATED. Skipping webhook state change. orderId={}, status={}",
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


package com.backend.controller.payment;

import com.backend.dto.payment.request.TossPaymentConfirmRequest;
import com.backend.dto.payment.response.TossPaymentConfirmResponse;
import com.backend.service.payment.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Toss 결제 승인 콜백
     */
    @PostMapping("/toss/confirm")
    public ResponseEntity<TossPaymentConfirmResponse> confirmTossPayment(
            @Valid @RequestBody TossPaymentConfirmRequest request) {
        TossPaymentConfirmResponse response = paymentService.confirmTossPayment(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Toss 웹훅 수신 엔드포인트
     * - 10초 내 200 응답
     * - 상태 동기화 및 멱등 처리
     */
    @PostMapping("/toss/webhook")
    public ResponseEntity<Void> handleTossWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "tosspayments-webhook-transmission-time", required = false) String transmissionTime,
            @RequestHeader(value = "tosspayments-webhook-signature", required = false) String signature
    ) {
        paymentService.handleTossWebhook(payload, transmissionTime, signature);
        return ResponseEntity.ok().build();
    }
}


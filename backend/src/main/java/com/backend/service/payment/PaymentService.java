package com.backend.service.payment;

import com.backend.dto.payment.request.TossPaymentConfirmRequest;
import com.backend.dto.payment.response.PaymentReadyResponse;
import com.backend.dto.payment.response.TossPaymentConfirmResponse;

public interface PaymentService {

    PaymentReadyResponse prepareTossPayment(String orderNo, Long memberId);

    TossPaymentConfirmResponse confirmTossPayment(TossPaymentConfirmRequest request);

    /**
     * Toss 웹훅 처리 (결제 상태 동기화)
     *
     * @param payload           웹훅 JSON 본문
     * @param transmissionTime  헤더 tosspayments-webhook-transmission-time
     * @param signature         헤더 tosspayments-webhook-signature (선택)
     */
    void handleTossWebhook(String payload, String transmissionTime, String signature);
}


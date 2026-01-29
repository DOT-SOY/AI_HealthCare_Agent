package com.backend.repository.payment;

import com.backend.domain.payment.Payment;
import com.backend.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = {"order"})
    Optional<Payment> findByPaymentKey(String paymentKey);

    Optional<Payment> findTopByOrder_OrderNoOrderByIdDesc(String orderNo);

    Optional<Payment> findByOrder_OrderNoAndStatus(String orderNo, PaymentStatus status);
}


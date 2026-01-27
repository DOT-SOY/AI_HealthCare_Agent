package com.backend.repository.payment;

import com.backend.domain.payment.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentKey(String paymentKey);

    Optional<Payment> findTopByOrder_OrderNoOrderByIdDesc(String orderNo);
}


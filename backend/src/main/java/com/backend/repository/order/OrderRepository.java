package com.backend.repository.order;

import com.backend.domain.order.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNo(String orderNo);

    @EntityGraph(attributePaths = {"items", "items.variant", "buyerSnapshot", "shipToSnapshot"})
    Optional<Order> findDetailByOrderNo(String orderNo);

    @EntityGraph(attributePaths = {"items", "buyerSnapshot", "shipToSnapshot"})
    Optional<Order> findWithDetailsByOrderNo(String orderNo);
}


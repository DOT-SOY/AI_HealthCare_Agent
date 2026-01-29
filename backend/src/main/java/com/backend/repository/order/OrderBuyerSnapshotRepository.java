package com.backend.repository.order;

import com.backend.domain.order.OrderBuyerSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderBuyerSnapshotRepository extends JpaRepository<OrderBuyerSnapshot, Long> {
}


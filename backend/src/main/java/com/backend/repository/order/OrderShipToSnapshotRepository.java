package com.backend.repository.order;

import com.backend.domain.order.OrderShipToSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderShipToSnapshotRepository extends JpaRepository<OrderShipToSnapshot, Long> {
}


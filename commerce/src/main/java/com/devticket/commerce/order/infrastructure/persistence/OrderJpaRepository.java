package com.devticket.commerce.order.infrastructure.persistence;

import com.devticket.commerce.order.domain.model.Order;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderId(UUID orderId);
}

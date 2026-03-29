package com.devticket.commerce.order.infrastructure.persistence;

import com.devticket.commerce.order.domain.model.OrderItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemJpaRepository extends JpaRepository<OrderItem, Long> {


    List<OrderItem> findAllByOrderId(UUID orderId);
}

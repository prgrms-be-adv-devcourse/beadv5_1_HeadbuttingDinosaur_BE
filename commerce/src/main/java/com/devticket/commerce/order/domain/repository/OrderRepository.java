package com.devticket.commerce.order.domain.repository;

import com.devticket.commerce.order.domain.model.Order;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findByOrderId(UUID orderId);
}

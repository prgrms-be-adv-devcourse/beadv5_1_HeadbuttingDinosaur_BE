package com.devticket.commerce.order.domain.repository;

import com.devticket.commerce.order.domain.model.Order;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long id);

    List<Order> findAllByIds(List<Long> id);

    Optional<Order> findByOrderId(UUID orderId);

    List<Order> findAllByOrderIds(List<UUID> orderIds);

}

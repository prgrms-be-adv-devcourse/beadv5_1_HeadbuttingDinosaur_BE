package com.devticket.commerce.order.domain.repository;

import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.order.domain.model.Order;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long id);

    List<Order> findAllByIds(List<Long> id);

    Optional<Order> findByOrderId(UUID orderId);

    List<Order> findAllByOrderIds(List<UUID> orderIds);

    Page<Order> findAllByUserId(UUID userId, OrderStatus status, Pageable pageable);

    List<Order> findExpiredOrders(OrderStatus status, int expirationMinutes);

}

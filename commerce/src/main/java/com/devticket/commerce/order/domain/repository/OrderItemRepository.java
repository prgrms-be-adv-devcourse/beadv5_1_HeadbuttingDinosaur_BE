package com.devticket.commerce.order.domain.repository;

import com.devticket.commerce.order.domain.model.OrderItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderItemRepository {

    OrderItem save(OrderItem OrderItem);

    List<OrderItem> findAllByOrderId(Long orderId);

    List<OrderItem> saveAll(List<OrderItem> orderItems);

    List<OrderItem> findSettlementItems(List<UUID> eventIds);

    List<OrderItem> findAllByEventId(UUID eventId);

    Optional<OrderItem> findByOrderItemId(UUID orderItemId);
}

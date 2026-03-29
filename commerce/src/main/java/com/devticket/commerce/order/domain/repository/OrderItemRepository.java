package com.devticket.commerce.order.domain.repository;

import com.devticket.commerce.order.domain.model.OrderItem;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OrderItemRepository {

    OrderItem save(OrderItem OrderItem);

    List<OrderItem> findAllByOrderId(Long orderId);

    List<OrderItem> saveAll(List<OrderItem> orderItems);

    List<OrderItem> findSettlementItems(UUID sellerId, LocalDateTime periodStart, LocalDateTime periodEnd);

    List<OrderItem> findAllByEventId(Long eventId);
}

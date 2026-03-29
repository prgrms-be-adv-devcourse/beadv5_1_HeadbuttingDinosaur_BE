package com.devticket.commerce.order.domain.repository;

import com.devticket.commerce.order.domain.model.OrderItem;
import java.util.List;
import java.util.UUID;

public interface OrderItemRepository {

    OrderItem save(OrderItem OrderItem);

    List<OrderItem> findAllByOrderId(UUID orderId);
    List<OrderItem> saveAll(List<OrderItem> orderItems);
    
}

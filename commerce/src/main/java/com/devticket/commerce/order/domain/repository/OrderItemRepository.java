package com.devticket.commerce.order.domain.repository;

import com.devticket.commerce.order.domain.model.OrderItem;

public interface OrderItemRepository {

    OrderItem save(OrderItem OrderItem);
}

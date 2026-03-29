package com.devticket.commerce.order.domain.repository;

import com.devticket.commerce.order.domain.model.OrderItem;
import java.util.List;

public interface OrderItemRepository {

    OrderItem save(OrderItem OrderItem);

    List<OrderItem> saveAll(List<OrderItem> orderItems);
    
}

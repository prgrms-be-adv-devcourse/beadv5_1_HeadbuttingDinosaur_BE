package com.devticket.commerce.order.domain.repository;

import com.devticket.commerce.order.domain.model.Order;

public interface OrderRepository {

    Order save(Order order);
}

package com.devticket.commerce.order.presentation.dto.res;

import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.order.domain.model.Order;
import java.time.LocalDateTime;
import java.util.UUID;

public record OrderStatusResponse(
    UUID orderId,
    OrderStatus status,
    LocalDateTime updatedAt
) {

    public static OrderStatusResponse of(Order order) {
        return new OrderStatusResponse(
            order.getOrderId(),
            order.getStatus(),
            order.getUpdatedAt()
        );
    }
}

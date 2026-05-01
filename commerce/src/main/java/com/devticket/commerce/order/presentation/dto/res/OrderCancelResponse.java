package com.devticket.commerce.order.presentation.dto.res;

import com.devticket.commerce.order.domain.model.Order;
import java.time.LocalDateTime;

public record OrderCancelResponse(

    String orderId,

    String status,

    String cancelledAt

) {

    public static OrderCancelResponse of(Order order) {
        return new OrderCancelResponse(
            order.getOrderId().toString(),
            order.getStatus().name(),
            LocalDateTime.now().toString()
        );
    }
}

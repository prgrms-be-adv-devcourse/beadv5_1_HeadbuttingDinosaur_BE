package com.devticket.commerce.order.presentation.dto.res;

import com.devticket.commerce.order.domain.model.Order;
import java.util.UUID;

public record InternalOrderInfoResponse(
    UUID id,
    UUID userId,
    String orderNumber,
    String paymentMethod,
    Integer totalAmount,
    String status,
    String orderedAt
) {

    public static InternalOrderInfoResponse from(Order order) {
        return new InternalOrderInfoResponse(
            order.getOrderId(),
            order.getUserId(),
            order.getOrderNumber(),
            order.getPaymentMethod() != null ? order.getPaymentMethod().name() : null,
            order.getTotalAmount(),
            order.getStatus().name(),
            order.getOrderedAt().toString()
        );
    }
}

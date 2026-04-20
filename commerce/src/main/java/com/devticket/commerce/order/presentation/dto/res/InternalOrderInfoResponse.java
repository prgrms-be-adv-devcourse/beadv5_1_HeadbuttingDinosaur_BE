package com.devticket.commerce.order.presentation.dto.res;

import com.devticket.commerce.order.domain.model.Order;
import java.util.List;
import java.util.UUID;

public record InternalOrderInfoResponse(
    UUID id,
    UUID userId,
    String orderNumber,
    String paymentMethod,
    Integer totalAmount,
    String status,
    String orderedAt,
    List<OrderItem> orderItems
) {

    public record OrderItem(UUID eventId, int quantity) {}

    public static InternalOrderInfoResponse from(
        Order order,
        List<com.devticket.commerce.order.domain.model.OrderItem> orderItemEntities
    ) {
        List<OrderItem> items = orderItemEntities.stream()
            .map(entity -> new OrderItem(entity.getEventId(), entity.getQuantity()))
            .toList();
        return new InternalOrderInfoResponse(
            order.getOrderId(),
            order.getUserId(),
            order.getOrderNumber(),
            order.getPaymentMethod() != null ? order.getPaymentMethod().name() : null,
            order.getTotalAmount(),
            order.getStatus().name(),
            order.getOrderedAt().toString(),
            items
        );
    }
}

package com.devticket.commerce.order.presentation.dto.res;

import com.devticket.commerce.order.domain.model.OrderItem;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;

@Builder
public record OrderItemResponse(
    Long id,
    UUID orderItemId,
    Long orderId,
    UUID userId,
    Long eventId,
    int price,
    int quantity,
    int subtotalAmount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    public static OrderItemResponse from(OrderItem orderItem) {
        return OrderItemResponse.builder()
            .id(orderItem.getId())
            .orderItemId(orderItem.getOrderItemId())
            .orderId(orderItem.getOrderId())
            .userId(orderItem.getUserId())
            .eventId(orderItem.getEventId())
            .price(orderItem.getPrice())
            .quantity(orderItem.getQuantity())
            .subtotalAmount(orderItem.getSubtotalAmount())
            .createdAt(orderItem.getCreatedAt())
            .updatedAt(orderItem.getUpdatedAt())
            .build();
    }
}
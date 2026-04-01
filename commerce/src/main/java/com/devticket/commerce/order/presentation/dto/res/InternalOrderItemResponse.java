package com.devticket.commerce.order.presentation.dto.res;

import com.devticket.commerce.order.domain.model.OrderItem;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;

@Builder
public record InternalOrderItemResponse(
    Long id,
    UUID orderItemId,
    Long orderId,
    UUID userId,
    UUID eventId,
    int price,
    int quantity,
    int subtotalAmount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    public static InternalOrderItemResponse from(OrderItem orderItem) {
        return InternalOrderItemResponse.builder()
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
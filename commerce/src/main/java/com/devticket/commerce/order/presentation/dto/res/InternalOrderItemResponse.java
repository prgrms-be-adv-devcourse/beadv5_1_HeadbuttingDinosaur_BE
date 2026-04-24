package com.devticket.commerce.order.presentation.dto.res;

import com.devticket.commerce.order.domain.model.OrderItem;
import java.util.UUID;
import lombok.Builder;

@Builder
public record InternalOrderItemResponse(
    UUID orderItemId,
    UUID orderId,
    UUID userId,
    UUID eventId,
    Integer amount
) {

    public static InternalOrderItemResponse from(OrderItem orderItem, UUID orderUuid) {
        return new InternalOrderItemResponse(
            orderItem.getOrderItemId(),
            orderUuid,
            orderItem.getUserId(),
            orderItem.getEventId(),
            orderItem.getSubtotalAmount()
        );
    }
}
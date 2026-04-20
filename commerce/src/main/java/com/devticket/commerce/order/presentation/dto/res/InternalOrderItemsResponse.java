package com.devticket.commerce.order.presentation.dto.res;

import com.devticket.commerce.order.domain.model.OrderItem;
import java.util.List;
import java.util.UUID;

public record InternalOrderItemsResponse(
    Long eventId,
    List<InternalOrderItemsResponse.OrderItems> orders
) {

    public record OrderItems(
        Long orderId,
        UUID eventId,
        Integer price,
        Integer quantity,
        Integer subtotalAmount
    ) {

        public static OrderItems from(OrderItem orderItem) {
            return new OrderItems(
                orderItem.getOrderId(),
                orderItem.getEventId(),
                orderItem.getPrice(),
                orderItem.getQuantity(),
                orderItem.getSubtotalAmount()
            );
        }

    }

    public static InternalOrderItemsResponse from(Long eventId, List<OrderItem> orderItems) {
        return new InternalOrderItemsResponse(
            eventId,
            orderItems.stream()
                .map(OrderItems::from)
                .toList()
        );
    }


}


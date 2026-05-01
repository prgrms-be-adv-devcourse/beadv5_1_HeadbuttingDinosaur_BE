package com.devticket.commerce.order.presentation.dto.res;

import com.devticket.commerce.order.domain.model.OrderItem;
import java.util.UUID;
import lombok.Builder;

@Builder
public record OrderItemsResponse(
    UUID eventId,
    String eventTitle,
    int quantity,
    int price
) {

    public static OrderItemsResponse of(OrderItem orderItem, String eventTitle) {
        return OrderItemsResponse.builder()
            .eventId(orderItem.getEventId())
            .eventTitle(eventTitle)
            .quantity(orderItem.getQuantity())
            .price(orderItem.getPrice())
            .build();
    }

}
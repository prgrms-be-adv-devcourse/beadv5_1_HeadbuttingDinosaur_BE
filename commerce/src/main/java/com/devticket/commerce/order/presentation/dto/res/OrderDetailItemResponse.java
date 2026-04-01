package com.devticket.commerce.order.presentation.dto.res;

import com.devticket.commerce.order.domain.model.OrderItem;
import java.util.UUID;
import lombok.Builder;

@Builder
public record OrderDetailItemResponse(
    UUID eventId,
    String eventTitle,
    int quantity,
    int price
) {

    public static OrderDetailItemResponse of(OrderItem orderItem, String eventTitle) {
        return OrderDetailItemResponse.builder()
            .eventId(orderItem.getEventId())
            .eventTitle(eventTitle)
            .quantity(orderItem.getQuantity())
            .price(orderItem.getPrice())
            .build();
    }
}

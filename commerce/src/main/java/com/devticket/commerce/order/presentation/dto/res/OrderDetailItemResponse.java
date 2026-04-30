package com.devticket.commerce.order.presentation.dto.res;

import com.devticket.commerce.order.domain.model.OrderItem;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record OrderDetailItemResponse(
    UUID eventId,
    String eventTitle,
    int quantity,
    int price,
    List<UUID> ticketIds
) {

    public static OrderDetailItemResponse of(OrderItem orderItem, String eventTitle, List<UUID> ticketIds) {
        return OrderDetailItemResponse.builder()
            .eventId(orderItem.getEventId())
            .eventTitle(eventTitle)
            .quantity(orderItem.getQuantity())
            .price(orderItem.getPrice())
            .ticketIds(ticketIds)
            .build();
    }
}

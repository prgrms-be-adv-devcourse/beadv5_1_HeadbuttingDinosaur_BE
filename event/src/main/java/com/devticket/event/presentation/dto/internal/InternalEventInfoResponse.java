package com.devticket.event.presentation.dto.internal;

import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.model.Event;

import java.time.LocalDateTime;
import java.util.UUID;

public record InternalEventInfoResponse(
    Long id,
    UUID sellerId,
    String title,
    Integer price,
    EventStatus status,
    EventCategory category,
    Integer totalQuantity,
    Integer maxQuantity,
    Integer remainingQuantity,
    LocalDateTime eventDateTime,
    LocalDateTime saleStartAt,
    LocalDateTime saleEndAt
) {

    public static InternalEventInfoResponse from(Event event) {
        return new InternalEventInfoResponse(
            event.getId(),
            event.getSellerId(),
            event.getTitle(),
            event.getPrice(),
            event.getStatus(),
            event.getCategory(),
            event.getTotalQuantity(),
            event.getMaxQuantity(),
            event.getRemainingQuantity(),
            event.getEventDateTime(),
            event.getSaleStartAt(),
            event.getSaleEndAt()
        );
    }
}

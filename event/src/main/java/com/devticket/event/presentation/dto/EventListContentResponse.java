package com.devticket.event.presentation.dto;

import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.model.Event;
import java.time.LocalDateTime;
import java.util.UUID;

public record EventListContentResponse(
    UUID eventId,
    String title,
    EventCategory category,
    EventStatus status,
    LocalDateTime eventDateTime,
    LocalDateTime saleStartAt,
    LocalDateTime saleEndAt,
    Integer price,
    Integer totalQuantity,
    Integer remainingQuantity,
    LocalDateTime createdAt
) {
    public static EventListContentResponse from(Event event) {
        return new EventListContentResponse(
            event.getEventId(),
            event.getTitle(),
            event.getCategory(),
            event.getStatus(),
            event.getEventDateTime(),
            event.getSaleStartAt(),
            event.getSaleEndAt(),
            event.getPrice(),
            event.getTotalQuantity(),
            event.getRemainingQuantity(),
            event.getCreatedAt()
        );
    }
}

package com.devticket.event.presentation.dto;

import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.model.Event;
import java.util.UUID;

public record EventListContentResponse(
    UUID eventId,
    String title,
    EventCategory category,
    Integer price,
    Integer totalQuantity,
    Integer remainingQuantity
) {
    public static EventListContentResponse from(Event event) {
        return new EventListContentResponse(
            event.getEventId(),
            event.getTitle(),
            event.getCategory(),
            event.getPrice(),
            event.getTotalQuantity(),
            event.getRemainingQuantity()
        );
    }
}

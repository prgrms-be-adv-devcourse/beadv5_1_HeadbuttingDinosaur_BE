package com.devticket.event.presentation.dto;

import com.devticket.event.domain.enums.EventCategory;
import com.devticket.event.domain.model.Event;

import java.time.LocalDateTime;
import java.util.UUID;

public record EventDetailResponse(
    UUID eventId,
    String title,
    String description,
    String location,
    LocalDateTime eventDateTime,
    LocalDateTime saleStartAt,
    LocalDateTime saleEndAt,
    Integer price,
    Integer totalQuantity,
    Integer maxQuantity,
    EventCategory category
) {

    public static EventDetailResponse from(Event event) {
        return new EventDetailResponse(
            event.getEventId(),
            event.getTitle(),
            event.getDescription(),
            event.getLocation(),
            event.getEventDateTime(),
            event.getSaleStartAt(),
            event.getSaleEndAt(),
            event.getPrice(),
            event.getTotalQuantity(),
            event.getMaxQuantity(),
            event.getCategory()
        );
    }
}

package com.devticket.event.presentation.dto;

import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.model.Event;
import java.time.LocalDateTime;
import java.util.UUID;

public record SellerEventSummaryResponse(
    UUID eventId,
    String title,
    EventStatus status,
    Integer totalQuantity,
    Integer soldQuantity,
    Integer remainingQuantity,
    LocalDateTime saleEndAt,
    LocalDateTime eventDateTime
) {
    public static SellerEventSummaryResponse from(Event event) {
        return new SellerEventSummaryResponse(
            event.getEventId(),
            event.getTitle(),
            event.getStatus(),
            event.getTotalQuantity(),
            event.getTotalQuantity() - event.getRemainingQuantity(),
            event.getRemainingQuantity(),
            event.getSaleEndAt(),
            event.getEventDateTime()
        );
    }
}

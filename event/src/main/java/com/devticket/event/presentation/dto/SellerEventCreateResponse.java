package com.devticket.event.presentation.dto;

import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.model.Event;
import java.time.LocalDateTime;
import java.util.UUID;

public record SellerEventCreateResponse(
    UUID eventId,
    EventStatus status,
    LocalDateTime createdAt
) {
    public static SellerEventCreateResponse from(Event event) {
        return new SellerEventCreateResponse(
            event.getEventId(),
            event.getStatus(),
            event.getCreatedAt()
        );
    }
}

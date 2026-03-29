package com.devticket.event.presentation.dto;

import com.devticket.event.domain.enums.EventStatus;
import com.devticket.event.domain.model.Event;
import java.time.LocalDateTime;
import java.util.UUID;

public record SellerEventUpdateResponse(
    UUID eventId,
    EventStatus status,
    LocalDateTime updatedAt
) {
    public static SellerEventUpdateResponse from(Event event) {
        return new SellerEventUpdateResponse(
            event.getEventId(),
            event.getStatus(),
            event.getUpdatedAt()
        );
    }
}

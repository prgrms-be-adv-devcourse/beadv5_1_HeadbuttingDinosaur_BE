package com.devticket.event.presentation.dto.internal;

import com.devticket.event.domain.model.Event;
import java.time.LocalDateTime;
import java.util.UUID;

public record InternalAdminEventResponse(
    UUID eventId,
    String title,
    String sellerNickname,
    String status,
    LocalDateTime eventDateTime,
    Integer totalQuantity,
    Integer remainingQuantity
) {
    public static InternalAdminEventResponse of(Event event, String sellerNickname) {
        return new InternalAdminEventResponse(
            event.getEventId(),
            event.getTitle(),
            sellerNickname,
            event.getStatus().name(),
            event.getEventDateTime(),
            event.getTotalQuantity(),
            event.getRemainingQuantity()
        );
    }
}

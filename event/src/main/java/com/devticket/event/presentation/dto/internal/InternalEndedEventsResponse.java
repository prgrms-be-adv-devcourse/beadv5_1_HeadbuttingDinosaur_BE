package com.devticket.event.presentation.dto.internal;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record InternalEndedEventsResponse(
    List<EndedEventItem> events
) {

    public record EndedEventItem(
        Long id,
        UUID eventId,
        UUID sellerId,
        LocalDateTime eventDateTime
    ) {}
}
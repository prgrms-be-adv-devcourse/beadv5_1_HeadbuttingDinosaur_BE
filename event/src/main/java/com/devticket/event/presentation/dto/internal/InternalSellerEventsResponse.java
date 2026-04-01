package com.devticket.event.presentation.dto.internal;

import com.devticket.event.domain.enums.EventStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record InternalSellerEventsResponse(
    UUID sellerId,
    List<SellerEventSummary> events
) {

    public record SellerEventSummary(
        UUID eventId,
        String title,
        Integer price,
        Integer totalQuantity,
        Integer remainingQuantity,
        EventStatus status,
        LocalDateTime eventDateTime
    ) {}
}

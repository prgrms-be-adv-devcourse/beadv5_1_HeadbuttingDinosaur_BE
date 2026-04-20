package com.devticket.event.application.event;

import com.devticket.event.common.messaging.event.ActionType;
import java.time.Instant;
import java.util.UUID;

public record ActionLogDomainEvent(
        UUID userId,
        UUID eventId,
        ActionType actionType,
        String searchKeyword,
        String stackFilter,
        Integer dwellTimeSeconds,
        Integer quantity,
        Long totalAmount,
        Instant timestamp
) {
}

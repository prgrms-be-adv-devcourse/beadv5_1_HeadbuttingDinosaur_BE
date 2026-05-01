package com.devticket.event.common.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ActionLogEvent(
        String userId,
        String eventId,
        String actionType,
        String searchKeyword,
        String stackFilter,
        Integer dwellTimeSeconds,
        Integer quantity,
        Long totalAmount,
        Instant timestamp
) {
}

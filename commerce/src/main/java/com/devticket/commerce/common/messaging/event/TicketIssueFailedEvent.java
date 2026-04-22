package com.devticket.commerce.common.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TicketIssueFailedEvent(
        UUID orderId,
        UUID userId,
        UUID paymentId,
        List<FailedItem> items,
        int totalAmount,
        String reason,
        Instant timestamp
) {
    public record FailedItem(
            UUID eventId,
            int quantity
    ) {}
}

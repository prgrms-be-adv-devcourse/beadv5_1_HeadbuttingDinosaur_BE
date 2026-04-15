package com.devticket.commerce.common.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record TicketIssueFailedEvent(
        UUID orderId,
        UUID userId,
        UUID eventId,
        UUID paymentId,
        int quantity,
        int totalAmount,
        String reason,
        Instant timestamp
) {}

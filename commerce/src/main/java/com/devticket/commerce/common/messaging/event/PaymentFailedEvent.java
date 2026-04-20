package com.devticket.commerce.common.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID orderId,
        UUID userId,
        List<OrderItem> orderItems,
        String reason,
        Instant timestamp
) {
    public record OrderItem(
            UUID eventId,
            int quantity
    ) {}
}

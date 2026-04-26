package com.devticket.commerce.common.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID orderId,
        UUID userId,
        List<OrderItem> orderItems,
        int totalAmount,
        Instant timestamp
) {
    public record OrderItem(
            UUID eventId,
            int quantity
    ) {}
}

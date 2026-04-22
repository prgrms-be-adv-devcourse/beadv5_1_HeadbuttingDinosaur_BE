package com.devticket.commerce.common.messaging.event;

import com.devticket.commerce.common.enums.PaymentMethod;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentCompletedEvent(
        UUID orderId,
        UUID userId,
        UUID paymentId,
        PaymentMethod paymentMethod,
        int totalAmount,
        List<OrderItem> orderItems,
        Instant timestamp
) {
    public record OrderItem(
            UUID eventId,
            int quantity
    ) {}
}

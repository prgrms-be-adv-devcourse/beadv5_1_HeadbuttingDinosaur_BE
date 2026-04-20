package com.devticket.commerce.common.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record StockDeductedEvent(
        UUID orderId,
        UUID eventId,
        int quantity,
        Instant timestamp
) {}

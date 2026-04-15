package com.devticket.commerce.common.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record StockFailedEvent(
        UUID orderId,
        UUID eventId,
        String reason,
        Instant timestamp
) {}

package com.devticket.commerce.common.messaging.event.refund;

import java.time.Instant;
import java.util.UUID;

public record RefundOrderFailedEvent(
        UUID refundId,
        UUID orderId,
        String reason,
        Instant timestamp
) {}

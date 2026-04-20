package com.devticket.payment.refund.application.saga.event;

import java.time.Instant;
import java.util.UUID;

public record RefundOrderFailedEvent(
    UUID refundId,
    UUID orderId,
    String reason,
    Instant timestamp
) {
}

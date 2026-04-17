package com.devticket.payment.refund.application.event;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record RefundStockFailedEvent(
    UUID refundId,
    UUID orderId,
    UUID eventId,
    String reason,
    Instant timestamp
) {
}

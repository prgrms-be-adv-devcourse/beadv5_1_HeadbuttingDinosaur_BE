package com.devticket.payment.refund.application.event;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record RefundStockDoneEvent(
    UUID refundId,
    UUID orderId,
    UUID eventId,
    Instant timestamp
) {
}

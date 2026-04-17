package com.devticket.payment.refund.application.event;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record RefundStockRestoreEvent(
    UUID refundId,
    UUID orderId,
    UUID eventId,
    int quantity,
    Instant timestamp
) {
}

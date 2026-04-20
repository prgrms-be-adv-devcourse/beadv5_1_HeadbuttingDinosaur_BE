package com.devticket.payment.refund.application.saga.event;

import java.time.Instant;
import java.util.UUID;

public record RefundStockRestoreEvent(
    UUID refundId,
    UUID orderId,
    UUID eventId,
    int quantity,
    Instant timestamp
) {
}

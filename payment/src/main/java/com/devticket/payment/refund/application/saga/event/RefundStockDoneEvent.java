package com.devticket.payment.refund.application.saga.event;

import java.time.Instant;
import java.util.UUID;

public record RefundStockDoneEvent(
    UUID refundId,
    UUID orderId,
    Instant timestamp
) {
}

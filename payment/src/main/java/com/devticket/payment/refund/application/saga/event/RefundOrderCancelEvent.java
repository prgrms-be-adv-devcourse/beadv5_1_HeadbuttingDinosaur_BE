package com.devticket.payment.refund.application.saga.event;

import java.time.Instant;
import java.util.UUID;

public record RefundOrderCancelEvent(
    UUID refundId,
    UUID orderId,
    boolean wholeOrder,
    Instant timestamp
) {
}

package com.devticket.payment.refund.application.event;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record RefundOrderCancelEvent(
    UUID refundId,
    UUID orderId,
    boolean fullRefund,
    Instant timestamp
) {
}

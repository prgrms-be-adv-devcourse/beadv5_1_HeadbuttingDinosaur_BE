package com.devticket.payment.refund.application.event;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record RefundTicketFailedEvent(
    UUID refundId,
    UUID orderId,
    String reason,
    Instant timestamp
) {
}

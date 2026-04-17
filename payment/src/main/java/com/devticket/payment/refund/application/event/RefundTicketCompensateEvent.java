package com.devticket.payment.refund.application.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record RefundTicketCompensateEvent(
    UUID refundId,
    UUID orderId,
    List<UUID> ticketIds,
    String reason,
    Instant timestamp
) {
}

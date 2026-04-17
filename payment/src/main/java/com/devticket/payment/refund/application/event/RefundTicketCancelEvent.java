package com.devticket.payment.refund.application.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record RefundTicketCancelEvent(
    UUID refundId,
    UUID orderId,
    List<UUID> ticketIds,
    Instant timestamp
) {
}

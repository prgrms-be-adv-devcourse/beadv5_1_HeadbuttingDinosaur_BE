package com.devticket.payment.refund.application.saga.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TicketIssueFailedEvent(
    UUID orderId,
    UUID userId,
    UUID paymentId,
    UUID eventId,
    List<UUID> issuedTicketIds,
    int refundAmount,
    String reason,
    Instant timestamp
) {
}

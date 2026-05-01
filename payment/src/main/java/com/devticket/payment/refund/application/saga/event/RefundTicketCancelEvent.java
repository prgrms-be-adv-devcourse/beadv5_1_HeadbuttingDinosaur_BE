package com.devticket.payment.refund.application.saga.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RefundTicketCancelEvent(
    UUID refundId,
    UUID orderId,
    List<UUID> ticketIds,
    boolean wholeOrder,
    Instant timestamp
) {
}

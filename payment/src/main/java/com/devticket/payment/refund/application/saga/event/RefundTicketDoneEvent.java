package com.devticket.payment.refund.application.saga.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RefundTicketDoneEvent(
    UUID refundId,
    UUID orderId,
    List<UUID> ticketIds,
    UUID eventId,
    int quantity,
    Instant timestamp
) {}

package com.devticket.commerce.common.messaging.event.refund;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RefundTicketCompensateEvent(
        UUID refundId,
        UUID orderId,
        List<UUID> ticketIds,
        String reason,
        Instant timestamp
) {}

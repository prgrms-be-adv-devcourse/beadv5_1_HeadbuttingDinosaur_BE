package com.devticket.commerce.common.messaging.event.refund;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Payment Orchestrator → Commerce: Ticket 보상 롤백 요청 (CANCELLED → ISSUED)
public record RefundTicketCompensateEvent(
        UUID orderId,
        List<UUID> ticketIds,
        String reason,
        Instant timestamp
) {}

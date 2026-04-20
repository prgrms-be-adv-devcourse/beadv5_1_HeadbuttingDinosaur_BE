package com.devticket.commerce.common.messaging.event.refund;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Payment Orchestrator → Commerce: 대상 티켓 ISSUED → CANCELLED 일괄 전이 요청
public record RefundTicketCancelEvent(
        UUID orderId,
        List<UUID> ticketIds,
        Instant timestamp
) {}

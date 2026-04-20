package com.devticket.commerce.common.messaging.event.refund;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Payment Orchestrator → Commerce: 환불 Saga 최종 확정
public record RefundCompletedEvent(
        UUID orderId,
        List<UUID> ticketIds,
        int refundAmount,
        Instant timestamp
) {}

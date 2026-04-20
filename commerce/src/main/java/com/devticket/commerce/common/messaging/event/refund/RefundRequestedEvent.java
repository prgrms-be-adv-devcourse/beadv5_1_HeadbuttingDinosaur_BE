package com.devticket.commerce.common.messaging.event.refund;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 환불 Saga 진입점. ticketIds = null 이면 오더 전체 환불 (Admin/Seller fan-out 경로 포함)
public record RefundRequestedEvent(
        UUID orderId,
        UUID userId,
        String reason,
        List<UUID> ticketIds,
        Instant timestamp
) {}

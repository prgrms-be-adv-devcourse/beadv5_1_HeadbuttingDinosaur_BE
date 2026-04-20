package com.devticket.commerce.common.messaging.event.refund;

import java.time.Instant;
import java.util.UUID;

// Payment Orchestrator → Commerce: Order PAID → REFUND_PENDING 전이 요청
public record RefundOrderCancelEvent(
        UUID orderId,
        UUID userId,
        int refundAmount,
        Instant timestamp
) {}

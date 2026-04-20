package com.devticket.commerce.common.messaging.event.refund;

import java.time.Instant;
import java.util.UUID;

// Payment Orchestrator → Commerce: Order 보상 롤백 요청 (REFUND_PENDING → PAID)
public record RefundOrderCompensateEvent(
        UUID orderId,
        String reason,
        Instant timestamp
) {}

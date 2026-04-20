package com.devticket.commerce.common.messaging.event.refund;

import java.time.Instant;
import java.util.UUID;

// Commerce → Payment: Order REFUND_PENDING 전이 실패 통지 (Saga 중단)
public record RefundOrderFailedEvent(
        UUID orderId,
        String reason,
        Instant timestamp
) {}

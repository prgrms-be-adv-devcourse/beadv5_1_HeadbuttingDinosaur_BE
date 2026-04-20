package com.devticket.commerce.common.messaging.event.refund;

import java.time.Instant;
import java.util.UUID;

// Commerce → Payment: Order 상태 전이 완료 통지.
public record RefundOrderDoneEvent(
        UUID refundId,
        UUID orderId,
        Instant timestamp
) {}

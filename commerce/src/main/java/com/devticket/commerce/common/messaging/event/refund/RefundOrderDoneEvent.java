package com.devticket.commerce.common.messaging.event.refund;

import java.time.Instant;
import java.util.UUID;

// Commerce → Payment: Order REFUND_PENDING 전이 완료 통지
public record RefundOrderDoneEvent(
        UUID orderId,
        int refundAmount,
        Instant timestamp
) {}

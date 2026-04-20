package com.devticket.commerce.common.messaging.event.refund;

import java.time.Instant;
import java.util.UUID;

// Commerce → Payment: 티켓 전이 실패 (Payment는 order 보상 롤백 트리거)
public record RefundTicketFailedEvent(
        UUID orderId,
        String reason,
        Instant timestamp
) {}

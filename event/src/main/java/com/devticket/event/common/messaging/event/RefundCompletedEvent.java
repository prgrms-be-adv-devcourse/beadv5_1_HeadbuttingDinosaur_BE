package com.devticket.event.common.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Payment → Event: 환불 Saga 최종 완료 알림 (refund.completed)
 * 현 단계에서는 모니터링/로깅 용도로만 수신한다.
 */
public record RefundCompletedEvent(
        UUID refundId,
        UUID orderId,
        Instant occurredAt
) {}

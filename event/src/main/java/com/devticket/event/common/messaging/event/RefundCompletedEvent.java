package com.devticket.event.common.messaging.event;

import com.devticket.event.domain.enums.PaymentMethod;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/**
 * Payment → Event: 환불 Saga 최종 완료 알림 (refund.completed)
 * 현 단계에서는 모니터링/로깅 용도로만 수신한다.
 */
@Builder
public record RefundCompletedEvent(
    UUID refundId,
    UUID orderId,
    UUID userId,
    UUID paymentId,
    PaymentMethod paymentMethod,
    int refundAmount,
    int refundRate,
    Instant timestamp
) {}

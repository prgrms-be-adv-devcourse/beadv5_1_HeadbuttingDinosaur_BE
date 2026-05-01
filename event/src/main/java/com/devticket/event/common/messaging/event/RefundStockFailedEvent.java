package com.devticket.event.common.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event → Payment: 환불 Saga 재고 복구 실패 응답 (refund.stock.failed)
 * 비즈니스 실패(Event 없음, 도메인 위반 등) 시 발행. 인프라 예외는 발행하지 않고 재시도/DLT 로 처리한다.
 */
public record RefundStockFailedEvent(
        UUID refundId,
        UUID orderId,
        String reason,
        Instant occurredAt
) {}

package com.devticket.event.common.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event → Payment: 환불 Saga 재고 복구 성공 응답 (refund.stock.done)
 * 정책적 스킵(이미 취소된 이벤트) 도 성공으로 간주해 발행한다.
 */
public record RefundStockDoneEvent(
        UUID refundId,
        UUID orderId,
        Instant occurredAt
) {}

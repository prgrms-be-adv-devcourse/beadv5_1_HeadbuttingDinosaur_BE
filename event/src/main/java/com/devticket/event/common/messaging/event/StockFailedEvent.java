package com.devticket.event.common.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event → Commerce: 재고 부족 이벤트 (stock.failed)
 * order.created 수신 후 재고 부족 판정 시 eventId 단위로 발행
 */
public record StockFailedEvent(
        UUID orderId,
        UUID eventId,
        String reason,
        Instant timestamp
) {}

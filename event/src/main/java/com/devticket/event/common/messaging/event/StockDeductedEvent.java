package com.devticket.event.common.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event → Commerce: 재고 차감 성공 이벤트 (stock.deducted)
 * order.created 수신 후 재고 차감 성공 시 eventId 단위로 발행
 */
public record StockDeductedEvent(
        UUID orderId,
        UUID eventId,
        int quantity,
        Instant timestamp
) {}

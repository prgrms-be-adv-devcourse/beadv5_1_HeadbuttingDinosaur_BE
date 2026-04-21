package com.devticket.event.common.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Payment → Event: 환불 Saga 의 재고 복구 단계 명령 (refund.stock.restore)
 * Event 서비스는 items 의 (eventId, quantity) 만큼 재고를 복구하고
 * 결과로 refund.stock.done / refund.stock.failed 를 발행한다.
 */
public record RefundStockRestoreEvent(
    UUID refundId,
    UUID orderId,
    List<Item> items,
    Instant timestamp
) {
    public record Item(UUID eventId, int quantity) {}
}



package com.devticket.commerce.common.messaging.event.refund;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Commerce → Payment: 티켓 CANCELLED 전이 완료 + Stock 복구 단계 연결용 eventId/quantity 포함.
// 다중 이벤트 오더도 메시지 1건으로 묶어 발행 — Payment onTicketDone 이 items 그대로 stock.restore 로 전달.
public record RefundTicketDoneEvent(
    UUID refundId,
    UUID orderId,
    List<UUID> ticketIds,
    List<Item> items,
    Instant timestamp
) {

    public record Item(UUID eventId, int quantity) {

    }
}

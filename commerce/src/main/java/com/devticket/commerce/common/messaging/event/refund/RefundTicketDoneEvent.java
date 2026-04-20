package com.devticket.commerce.common.messaging.event.refund;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Commerce → Payment: 티켓 CANCELLED 전이 완료 + Stock 복구 단계 연결용 eventId/quantity 포함.
// 다중 이벤트 오더면 eventId 별로 여러 메시지를 분할 발행한다.
public record RefundTicketDoneEvent(
        UUID refundId,
        UUID orderId,
        List<UUID> ticketIds,
        UUID eventId,
        int quantity,
        Instant timestamp
) {}

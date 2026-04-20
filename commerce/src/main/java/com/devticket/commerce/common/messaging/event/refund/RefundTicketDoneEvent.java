package com.devticket.commerce.common.messaging.event.refund;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Commerce → Payment: 티켓 일괄 CANCELLED 전이 완료 통지
public record RefundTicketDoneEvent(
        UUID orderId,
        List<UUID> ticketIds,
        Instant timestamp
) {}

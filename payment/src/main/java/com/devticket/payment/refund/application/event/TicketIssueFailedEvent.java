package com.devticket.payment.refund.application.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Commerce → Payment: 티켓 발급 실패 시 환불 자동 개시.
 * Commerce 측 계약에 맞춘 필드 집합.
 */
@Builder
public record TicketIssueFailedEvent(
    UUID orderId,
    UUID userId,
    UUID paymentId,
    List<UUID> ticketIds,
    int refundAmount,
    String reason,
    Instant timestamp
) {
}

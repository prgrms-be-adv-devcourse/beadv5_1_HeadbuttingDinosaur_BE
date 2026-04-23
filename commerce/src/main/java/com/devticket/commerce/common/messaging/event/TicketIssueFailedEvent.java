package com.devticket.commerce.common.messaging.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Commerce → Payment: 결제는 성공했으나 티켓 발급이 실패한 경우의 환불 트리거.
// ticketIds 는 실패 시점 기준 발급된 티켓 (일반적으로 빈 리스트).
// refundAmount 는 결제 총액 — 전액 환불 대상.
public record TicketIssueFailedEvent(
        UUID orderId,
        UUID userId,
        UUID paymentId,
        List<UUID> ticketIds,
        int refundAmount,
        String reason,
        Instant timestamp
) {}

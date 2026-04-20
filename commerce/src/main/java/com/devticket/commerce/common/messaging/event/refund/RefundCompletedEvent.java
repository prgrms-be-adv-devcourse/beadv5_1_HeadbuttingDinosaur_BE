package com.devticket.commerce.common.messaging.event.refund;

import com.devticket.commerce.common.enums.PaymentMethod;
import java.time.Instant;
import java.util.UUID;

// Payment → Commerce: Saga 최종 확정.
// ticketIds 없음 — Commerce 는 orderId 기준으로 CANCELLED 티켓을 REFUNDED 로 일괄 전이.
public record RefundCompletedEvent(
        UUID refundId,
        UUID orderId,
        UUID userId,
        UUID paymentId,
        PaymentMethod paymentMethod,
        int refundAmount,
        int refundRate,
        Instant timestamp
) {}

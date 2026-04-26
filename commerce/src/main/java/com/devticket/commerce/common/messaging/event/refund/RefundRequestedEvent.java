package com.devticket.commerce.common.messaging.event.refund;

import com.devticket.commerce.common.enums.PaymentMethod;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Commerce fan-out → Payment Orchestrator 진입점.
// orderRefundId 는 null 로 보내면 Payment 가 upsert (Payment 가 원장 소유).
public record RefundRequestedEvent(
    UUID refundId,
    UUID orderRefundId,
    UUID orderId,
    UUID userId,
    UUID paymentId,
    PaymentMethod paymentMethod,
    List<UUID> ticketIds,
    int refundAmount,
    int refundRate,
    boolean wholeOrder,
    String reason,
    Instant timestamp
) {

}

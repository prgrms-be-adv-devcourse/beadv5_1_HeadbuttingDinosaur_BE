package com.devticket.payment.refund.application.event;

import com.devticket.payment.payment.domain.enums.PaymentMethod;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
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
    String reason,
    Instant timestamp
) {
}

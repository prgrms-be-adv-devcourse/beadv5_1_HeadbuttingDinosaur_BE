package com.devticket.payment.refund.application.saga.event;

import com.devticket.payment.payment.domain.enums.PaymentMethod;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RefundRequestedEvent(
    UUID refundId,
    UUID orderId,
    UUID userId,
    UUID paymentId,
    PaymentMethod paymentMethod,
    List<UUID> ticketIds,
    int refundAmount,
    int refundRate,
    boolean wholeOrder,
    Instant timestamp
) {
}

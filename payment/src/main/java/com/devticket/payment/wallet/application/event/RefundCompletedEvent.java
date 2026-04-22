package com.devticket.payment.wallet.application.event;

import com.devticket.payment.payment.domain.enums.PaymentMethod;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record RefundCompletedEvent(
    UUID refundId,
    UUID orderId,
    UUID userId,
    UUID paymentId,
    PaymentMethod paymentMethod,
    int refundAmount,
    int refundRate,       // 0 | 50 | 100
    Instant timestamp
) {

}


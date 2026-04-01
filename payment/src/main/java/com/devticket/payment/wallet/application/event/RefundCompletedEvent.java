package com.devticket.payment.wallet.application.event;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;

@Builder
public record RefundCompletedEvent(
    String refundId,
    UUID orderId,
    String userId,
    String paymentId,
    String paymentMethod, // "WALLET" | "PG"
    int refundAmount,
    int refundRate,       // 0 | 50 | 100
    LocalDateTime timestamp
) {

}


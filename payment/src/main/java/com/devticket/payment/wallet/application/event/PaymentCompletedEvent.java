package com.devticket.payment.wallet.application.event;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;

@Builder
public record PaymentCompletedEvent(
    UUID orderId,
    String userId,
    String paymentId,
    String paymentMethod, // "WALLET" | "PG"
    int totalAmount,
    LocalDateTime timestamp
) {

}

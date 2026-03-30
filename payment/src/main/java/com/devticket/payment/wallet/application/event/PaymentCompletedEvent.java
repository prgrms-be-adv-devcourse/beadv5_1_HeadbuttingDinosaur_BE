package com.devticket.payment.wallet.application.event;

import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record PaymentCompletedEvent(
    Long orderId,
    String userId,
    String paymentId,
    String paymentMethod, // "WALLET" | "PG"
    int totalAmount,
    LocalDateTime timestamp
) {

}

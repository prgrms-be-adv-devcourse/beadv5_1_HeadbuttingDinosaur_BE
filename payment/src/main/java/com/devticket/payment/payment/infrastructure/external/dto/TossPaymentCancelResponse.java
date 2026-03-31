package com.devticket.payment.payment.infrastructure.external.dto;

public record TossPaymentCancelResponse(
    String paymentKey,
    String orderId,
    String status,
    Cancels[] cancels
) {
    public record Cancels(
        int cancelAmount,
        String cancelReason,
        String canceledAt
    ) {}
}

package com.devticket.payment.payment.infrastructure.external.dto;

public record TossPaymentStatusResponse(
    String paymentKey,
    String orderId,
    String status,       // READY | IN_PROGRESS | DONE | CANCELED | ABORTED | EXPIRED 등
    Integer totalAmount,
    String approvedAt
) {
}
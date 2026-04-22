package com.devticket.payment.payment.infrastructure.external.dto;

public record TossPaymentConfirmResponse(
    String paymentKey,
    String orderId,
    String method,
    String status,
    Integer totalAmount,
    String approvedAt
) {
}

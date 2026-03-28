package com.devticket.payment.payment.application.dto;

public record PgPaymentConfirmResult(
    String paymentKey,
    String orderId,
    String method,
    String status,
    Long totalAmount,
    String approvedAt
) {
}

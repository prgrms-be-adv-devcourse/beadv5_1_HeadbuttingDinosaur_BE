package com.devticket.payment.payment.application.dto;

public record PgPaymentCancelCommand(
    String paymentKey,
    int cancelAmount,
    String cancelReason
) {
}

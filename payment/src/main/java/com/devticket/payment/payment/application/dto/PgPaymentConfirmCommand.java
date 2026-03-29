package com.devticket.payment.payment.application.dto;

public record PgPaymentConfirmCommand(
    String paymentKey,
    String orderId,
    Integer amount
) {
}

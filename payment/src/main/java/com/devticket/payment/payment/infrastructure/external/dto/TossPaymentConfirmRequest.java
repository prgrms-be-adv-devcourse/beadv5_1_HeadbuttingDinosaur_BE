package com.devticket.payment.payment.infrastructure.external.dto;

public record TossPaymentConfirmRequest(
    String paymentKey,
    String orderId,
    Integer amount
) {
}

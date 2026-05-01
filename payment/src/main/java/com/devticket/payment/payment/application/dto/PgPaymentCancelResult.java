package com.devticket.payment.payment.application.dto;

public record PgPaymentCancelResult(
    String paymentKey,
    int cancelAmount,
    int totalCanceledAmount,
    String canceledAt
) {}

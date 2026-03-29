package com.devticket.payment.payment.infrastructure.external.dto;

public record TossPaymentCancelResponse(
    String paymentKey,
    String orderId,
    String status,       // CANCELED
    Long totalAmount,
    String requestedAt,
    String approvedAt
) {}
